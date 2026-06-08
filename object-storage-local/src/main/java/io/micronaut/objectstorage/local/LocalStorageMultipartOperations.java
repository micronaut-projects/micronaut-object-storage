/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadResponse;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadResponse;
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest;
import io.micronaut.objectstorage.multipart.ListMultipartPartsResponse;
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations;
import io.micronaut.objectstorage.multipart.MultipartPart;
import io.micronaut.objectstorage.multipart.MultipartUploadHandle;
import io.micronaut.objectstorage.multipart.UploadPartRequest;
import io.micronaut.objectstorage.multipart.UploadPartResponse;
import io.micronaut.objectstorage.request.FileUploadRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Local multipart upload operations.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.1.0
 */
@EachBean(LocalStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = LocalStorageConfiguration.class)
@Requires(beans = LocalStorageOperations.class)
@Primary
final class LocalStorageMultipartOperations implements MultipartObjectStorageOperations<
    LocalStorageMultipartOperations.LocalMultipartUpload,
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile> {

    private static final Logger LOG = LoggerFactory.getLogger(LocalStorageMultipartOperations.class);

    private static final String MULTIPART_PARTS_DIRECTORY = "parts";
    private static final String MULTIPART_UPLOAD_PROPERTIES = "upload.properties";
    private static final String MULTIPART_PART_EXTENSION = ".part";
    private static final String MULTIPART_PART_PROPERTIES_EXTENSION = ".properties";
    private static final String MULTIPART_COMPLETE_FILE_SUFFIX = ".complete";
    private static final String MULTIPART_CONTINUATION_TOKEN_PREFIX = "local-multipart:v1:";
    private static final String MULTIPART_KEY_PROPERTY = "key";
    private static final String MULTIPART_CONTENT_TYPE_PROPERTY = "contentType";
    private static final String MULTIPART_METADATA_PREFIX = "metadata.";
    private static final String MULTIPART_PART_ETAG_PROPERTY = "eTag";
    private static final String MULTIPART_PART_SIZE_PROPERTY = "size";
    private static final String MULTIPART_PART_FILE_PROPERTY = "file";

    private final LocalStorageLayout layout;
    private final LocalStorageOperations objectStorageOperations;
    private final boolean supportsPosixPermissions;

    LocalStorageMultipartOperations(@Parameter LocalStorageConfiguration configuration,
                                    LocalStorageOperations objectStorageOperations) {
        this.layout = new LocalStorageLayout(configuration);
        this.objectStorageOperations = objectStorageOperations;
        this.supportsPosixPermissions = layout.storageRoot().getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @Override
    @NonNull
    public CreateMultipartUploadResponse<LocalMultipartUpload> createMultipartUpload(@NonNull CreateMultipartUploadRequest request) {
        layout.objectPath(request.getKey());
        String uploadId = UUID.randomUUID().toString();
        Path uploadPath = multipartUploadPath(uploadId);
        if (!mkdirs(uploadPath)) {
            throw new ObjectStorageException("Error creating local multipart upload: " + uploadId);
        }
        Properties properties = new Properties();
        properties.setProperty(MULTIPART_KEY_PROPERTY, request.getKey());
        request.getContentType().ifPresent(contentType -> properties.setProperty(MULTIPART_CONTENT_TYPE_PROPERTY, contentType));
        request.getMetadata().forEach((key, value) -> properties.setProperty(MULTIPART_METADATA_PREFIX + key, value));
        try {
            storeRequiredProperties(uploadPath.resolve(MULTIPART_UPLOAD_PROPERTIES), properties);
        } catch (RuntimeException e) {
            deleteRecursively(uploadPath, e);
            throw e;
        }
        return CreateMultipartUploadResponse.of(
            new MultipartUploadHandle(request.getKey(), uploadId),
            new LocalMultipartUpload(uploadPath)
        );
    }

    @Override
    @NonNull
    public UploadPartResponse<LocalStorageOperations.LocalStorageFile> uploadPart(@NonNull UploadPartRequest request) {
        UploadSession session = retrieveUploadSession(request.getUpload());
        int partNumber = request.getPartNumber();
        Path partsPath = multipartPartsPath(session.path());
        Path partPropertiesFile = multipartPartPropertiesPath(session.path(), partNumber);
        if (!mkdirs(partsPath)) {
            throw new ObjectStorageException("Error creating local multipart part directories: " + partsPath);
        }
        String eTag = UUID.randomUUID().toString();
        String partFileName = partNumber + "-" + eTag + MULTIPART_PART_EXTENSION;
        Path partFile = multipartPartDataPath(session.path(), partFileName);
        Path temporaryPartFile = null;
        Path temporaryPartPropertiesFile = null;
        boolean partFileCommitted = false;
        boolean propertiesCommitted = false;
        try {
            temporaryPartFile = createTempFile(partsPath, partNumber + "-", MULTIPART_PART_EXTENSION + ".tmp");
            temporaryPartPropertiesFile = createTempFile(partsPath, partNumber + "-", MULTIPART_PART_PROPERTIES_EXTENSION + ".tmp");
            storeFile(temporaryPartFile, request.getUploadRequest().getInputStream());
            long partSize = size(temporaryPartFile);
            Properties properties = new Properties();
            properties.setProperty(MULTIPART_PART_ETAG_PROPERTY, eTag);
            properties.setProperty(MULTIPART_PART_SIZE_PROPERTY, Long.toString(partSize));
            properties.setProperty(MULTIPART_PART_FILE_PROPERTY, partFileName);
            storeRequiredProperties(temporaryPartPropertiesFile, properties);
            String previousPartFileName = retrieveCurrentPartFileName(partPropertiesFile).orElse(null);
            moveReplacing(temporaryPartFile, partFile);
            partFileCommitted = true;
            moveReplacing(temporaryPartPropertiesFile, partPropertiesFile);
            propertiesCommitted = true;
            deleteStalePartFile(session.path(), previousPartFileName, partFileName);
            return UploadPartResponse.of(
                new MultipartPart(partNumber, eTag, partSize),
                new LocalStorageOperations.LocalStorageFile(partFile)
            );
        } catch (RuntimeException e) {
            deleteIfExists(temporaryPartFile, e);
            deleteIfExists(temporaryPartPropertiesFile, e);
            if (partFileCommitted && !propertiesCommitted) {
                deleteIfExists(partFile, e);
            }
            throw e;
        }
    }

    @Override
    @NonNull
    public ListMultipartPartsResponse listParts(@NonNull ListMultipartPartsRequest request) {
        UploadSession session = retrieveUploadSession(request.getUpload());
        int partNumberMarker = decodeMultipartContinuationToken(request).orElse(0);
        List<MultipartPart> parts = retrieveMultipartParts(session.path()).stream()
            .filter(part -> part.getPartNumber() > partNumberMarker)
            .limit((long) request.getPageSize() + 1)
            .toList();
        if (parts.size() <= request.getPageSize()) {
            return new ListMultipartPartsResponse(parts);
        }
        List<MultipartPart> page = parts.subList(0, request.getPageSize());
        MultipartPart lastPart = page.get(page.size() - 1);
        return new ListMultipartPartsResponse(page, encodeMultipartContinuationToken(request, lastPart.getPartNumber()));
    }

    @Override
    @NonNull
    public CompleteMultipartUploadResponse<LocalStorageOperations.LocalStorageFile> completeMultipartUpload(
        @NonNull CompleteMultipartUploadRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        UploadSession session = retrieveUploadSession(upload);
        Path completedFile = buildCompletedMultipartFile(session.path(), request.getParts(), upload.getUploadId());
        try {
            var uploadRequest = new LocalCompletedMultipartUploadRequest(
                upload.getKey(),
                session.contentType(),
                completedFile,
                session.metadata()
            );
            var uploadResponse = objectStorageOperations.upload(uploadRequest);
            deleteMultipartUploadAfterCompletion(session.path());
            return CompleteMultipartUploadResponse.of(upload, uploadResponse.getETag(), uploadResponse.getNativeResponse());
        } catch (RuntimeException e) {
            deleteIfExists(completedFile, e);
            throw e;
        }
    }

    @Override
    public void abortMultipartUpload(@NonNull AbortMultipartUploadRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        Path uploadPath = multipartUploadPath(upload.getUploadId());
        Path propertiesPath = uploadPath.resolve(MULTIPART_UPLOAD_PROPERTIES);
        if (Files.exists(propertiesPath, LinkOption.NOFOLLOW_LINKS)) {
            validateUploadSessionKey(upload, retrieveRequiredProperties(propertiesPath));
        }
        deleteMultipartUpload(uploadPath);
    }

    @NonNull
    private UploadSession retrieveUploadSession(@NonNull MultipartUploadHandle upload) {
        Path uploadPath = multipartUploadPath(upload.getUploadId());
        Path propertiesPath = uploadPath.resolve(MULTIPART_UPLOAD_PROPERTIES);
        if (!Files.exists(propertiesPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ObjectStorageException("Local multipart upload does not exist: " + upload.getUploadId());
        }
        Properties properties = retrieveRequiredProperties(propertiesPath);
        validateUploadSessionKey(upload, properties);
        Map<String, String> metadata = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(MULTIPART_METADATA_PREFIX)) {
                metadata.put(name.substring(MULTIPART_METADATA_PREFIX.length()), properties.getProperty(name));
            }
        }
        return new UploadSession(uploadPath, Map.copyOf(metadata), nullIfEmpty(properties.getProperty(MULTIPART_CONTENT_TYPE_PROPERTY)));
    }

    @NonNull
    private List<MultipartPart> retrieveMultipartParts(@NonNull Path uploadPath) {
        Path partsPath = multipartPartsPath(uploadPath);
        if (!Files.exists(partsPath, LinkOption.NOFOLLOW_LINKS)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(partsPath)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(MULTIPART_PART_PROPERTIES_EXTENSION))
                .map(path -> retrieveMultipartPart(uploadPath, parsePartNumber(path, MULTIPART_PART_PROPERTIES_EXTENSION)))
                .sorted(Comparator.comparingInt(MultipartPart::getPartNumber))
                .toList();
        } catch (IOException e) {
            throw new ObjectStorageException("Error listing local multipart parts", e);
        }
    }

    @NonNull
    private MultipartPart retrieveMultipartPart(@NonNull Path uploadPath, int partNumber) {
        return retrieveStoredMultipartPart(uploadPath, partNumber).part();
    }

    @NonNull
    private StoredMultipartPart retrieveStoredMultipartPart(@NonNull Path uploadPath, int partNumber) {
        Path propertiesPath = multipartPartPropertiesPath(uploadPath, partNumber);
        if (!Files.exists(propertiesPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ObjectStorageException("Local multipart part does not exist: " + partNumber);
        }
        Properties properties = retrieveRequiredProperties(propertiesPath);
        String eTag = properties.getProperty(MULTIPART_PART_ETAG_PROPERTY);
        String size = properties.getProperty(MULTIPART_PART_SIZE_PROPERTY);
        String partFileName = properties.getProperty(MULTIPART_PART_FILE_PROPERTY);
        if (eTag == null || size == null || partFileName == null) {
            throw new ObjectStorageException("Local multipart part metadata is incomplete: " + partNumber);
        }
        Path partFile = multipartPartDataPath(uploadPath, partFileName);
        if (!Files.exists(partFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new ObjectStorageException("Local multipart part does not exist: " + partNumber);
        }
        try {
            long partSize = Long.parseLong(size);
            if (size(partFile) != partSize) {
                throw new ObjectStorageException("Local multipart part size does not match metadata: " + partNumber);
            }
            return new StoredMultipartPart(new MultipartPart(partNumber, eTag, partSize), partFile);
        } catch (NumberFormatException e) {
            throw new ObjectStorageException("Local multipart part size is invalid: " + partNumber, e);
        }
    }

    @NonNull
    private Path buildCompletedMultipartFile(@NonNull Path uploadPath,
                                             @NonNull List<MultipartPart> parts,
                                             @NonNull String uploadId) {
        Path completedFile = createTempFile(uploadPath, "micronaut-object-storage-local-" + uploadId + "-", MULTIPART_COMPLETE_FILE_SUFFIX);
        try (OutputStream out = LocalStorageIoSupport.newOutputStreamNoFollow(completedFile, supportsPosixPermissions)) {
            for (MultipartPart part : parts) {
                StoredMultipartPart storedPart = retrieveStoredMultipartPart(uploadPath, part.getPartNumber());
                if (!storedPart.part().getETag().equals(part.getETag())) {
                    throw new ObjectStorageException("Local multipart part ETag does not match: " + part.getPartNumber());
                }
                try (InputStream in = LocalStorageIoSupport.newInputStreamNoFollow(storedPart.path())) {
                    in.transferTo(out);
                }
            }
            return completedFile;
        } catch (IOException e) {
            ObjectStorageException objectStorageException = new ObjectStorageException("Error assembling local multipart upload: " + uploadId, e);
            deleteIfExists(completedFile, objectStorageException);
            throw objectStorageException;
        } catch (RuntimeException e) {
            deleteIfExists(completedFile, e);
            throw e;
        }
    }

    private Optional<Integer> decodeMultipartContinuationToken(ListMultipartPartsRequest request) {
        String continuationToken = request.getContinuationToken().orElse(null);
        if (continuationToken == null || continuationToken.isEmpty()) {
            return Optional.empty();
        }
        try {
            LocalMultipartContinuationToken decodedToken = LocalMultipartContinuationToken.decode(continuationToken);
            if (!decodedToken.matches(request)) {
                throw new ObjectStorageException("Local multipart continuation token does not match the current request");
            }
            return Optional.of(decodedToken.partNumberMarker());
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid local multipart continuation token", e);
        }
    }

    private String encodeMultipartContinuationToken(ListMultipartPartsRequest request, int partNumberMarker) {
        MultipartUploadHandle upload = request.getUpload();
        String payload = new StringJoiner("\n")
            .add(encodeTokenPart(upload.getKey()))
            .add(encodeTokenPart(upload.getUploadId()))
            .add(encodeTokenPart(Integer.toString(request.getPageSize())))
            .add(encodeTokenPart(Integer.toString(partNumberMarker)))
            .toString();
        return MULTIPART_CONTINUATION_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeTokenPart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeTokenPart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static ObjectStorageException invalidMultipartContinuationToken() {
        return new ObjectStorageException("Invalid local multipart continuation token");
    }

    private Path multipartUploadPath(String uploadId) {
        try {
            UUID.fromString(uploadId);
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid local multipart upload id: " + uploadId, e);
        }
        Path uploadPath = layout.multipartBucketDirectory().resolve(uploadId);
        LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), uploadPath);
        return uploadPath;
    }

    private Path multipartPartsPath(Path uploadPath) {
        Path partsPath = uploadPath.resolve(MULTIPART_PARTS_DIRECTORY);
        LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), partsPath);
        return partsPath;
    }

    private Path multipartPartDataPath(Path uploadPath, String fileName) {
        if (!fileName.endsWith(MULTIPART_PART_EXTENSION)) {
            throw new ObjectStorageException("Invalid local multipart part file: " + fileName);
        }
        Path partsPath = multipartPartsPath(uploadPath);
        Path partPath = partsPath.resolve(fileName).normalize();
        if (!partPath.getParent().equals(partsPath.normalize()) || !partPath.getFileName().toString().equals(fileName)) {
            throw new ObjectStorageException("Invalid local multipart part file: " + fileName);
        }
        LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), partPath);
        return partPath;
    }

    private Path multipartPartPropertiesPath(Path uploadPath, int partNumber) {
        Path propertiesPath = multipartPartsPath(uploadPath).resolve(partNumber + MULTIPART_PART_PROPERTIES_EXTENSION);
        LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), propertiesPath);
        return propertiesPath;
    }

    private int parsePartNumber(Path partFile, String extension) {
        String fileName = partFile.getFileName().toString();
        try {
            return Integer.parseInt(fileName.substring(0, fileName.length() - extension.length()));
        } catch (NumberFormatException e) {
            throw new ObjectStorageException("Invalid local multipart part file: " + fileName, e);
        }
    }

    private void validateUploadSessionKey(MultipartUploadHandle upload, Properties properties) {
        String key = properties.getProperty(MULTIPART_KEY_PROPERTY);
        if (!upload.getKey().equals(key)) {
            throw new ObjectStorageException("Multipart upload key does not match local upload session");
        }
    }

    private Optional<String> retrieveCurrentPartFileName(Path propertiesPath) {
        if (!Files.exists(propertiesPath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(retrieveRequiredProperties(propertiesPath).getProperty(MULTIPART_PART_FILE_PROPERTY));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private void deleteStalePartFile(Path uploadPath, @Nullable String previousPartFileName, String currentPartFileName) {
        if (previousPartFileName == null || previousPartFileName.equals(currentPartFileName)) {
            return;
        }
        try {
            Files.deleteIfExists(multipartPartDataPath(uploadPath, previousPartFileName));
        } catch (IOException | RuntimeException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error deleting stale local multipart part file: {}", previousPartFileName, e);
            }
        }
    }

    private void storeFile(Path file, InputStream inputStream) {
        if (!mkdirs(file.getParent())) {
            throw new ObjectStorageException("Error creating local multipart part directories: " + file);
        }
        try (OutputStream fileOut = LocalStorageIoSupport.newOutputStreamNoFollow(file, supportsPosixPermissions)) {
            inputStream.transferTo(fileOut);
        } catch (IOException e) {
            throw new ObjectStorageException("Error copying multipart part to: " + file, e);
        }
    }

    private void storeRequiredProperties(Path propertiesPath, Properties properties) {
        if (!mkdirs(propertiesPath.getParent())) {
            throw new ObjectStorageException("Error creating local multipart metadata directories: " + propertiesPath);
        }
        try (OutputStream out = LocalStorageIoSupport.newOutputStreamNoFollow(propertiesPath, supportsPosixPermissions)) {
            properties.store(out, "Local multipart upload state");
        } catch (IOException e) {
            throw new ObjectStorageException("Error storing local multipart metadata: " + propertiesPath, e);
        }
    }

    private void moveReplacing(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveException) {
                throw new ObjectStorageException("Error committing local multipart file: " + target, moveException);
            }
        } catch (IOException e) {
            throw new ObjectStorageException("Error committing local multipart file: " + target, e);
        }
    }

    private Properties retrieveRequiredProperties(Path propertiesPath) {
        Properties properties = new Properties();
        try (InputStream in = LocalStorageIoSupport.newInputStreamNoFollow(propertiesPath)) {
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading local multipart metadata: " + propertiesPath, e);
        }
    }

    private Path createTempFile(Path directory, String prefix, String suffix) {
        try {
            return LocalStorageIoSupport.createTempFile(directory, prefix, suffix, supportsPosixPermissions);
        } catch (IOException e) {
            throw new ObjectStorageException("Error creating local multipart temporary file", e);
        }
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading local multipart part size: " + path, e);
        }
    }

    private boolean mkdirs(Path path) {
        return LocalStorageIoSupport.mkdirs(layout.rootInternalDirectory(), path, supportsPosixPermissions);
    }

    private void deleteMultipartUpload(Path uploadPath) {
        try {
            LocalStorageBucketOperations.deleteRecursively(uploadPath);
        } catch (NoSuchFileException ignored) {
            // Abort must be safe to retry, and completed uploads have already removed their local session.
        } catch (IOException e) {
            throw new ObjectStorageException("Error deleting local multipart upload: " + uploadPath, e);
        }
        deleteEmptyMultipartDirectories(null);
    }

    private void deleteMultipartUploadAfterCompletion(Path uploadPath) {
        try {
            deleteMultipartUpload(uploadPath);
        } catch (RuntimeException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error deleting completed local multipart upload: {}", uploadPath, e);
            }
        }
    }

    private void deleteRecursively(Path path, Throwable failure) {
        try {
            LocalStorageBucketOperations.deleteRecursively(path);
        } catch (IOException | RuntimeException e) {
            failure.addSuppressed(e);
        }
        deleteEmptyMultipartDirectories(failure);
    }

    private void deleteIfExists(@Nullable Path path, Throwable failure) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
    }

    private void deleteEmptyMultipartDirectories(@Nullable Throwable failure) {
        for (Path directory : layout.multipartCleanupDirectories()) {
            try {
                Files.deleteIfExists(directory);
            } catch (DirectoryNotEmptyException ignored) {
                // Other local provider state still uses this directory.
            } catch (IOException e) {
                if (failure != null) {
                    failure.addSuppressed(e);
                } else {
                    throw new ObjectStorageException("Error deleting local multipart directory: " + directory, e);
                }
            }
        }
    }

    @Nullable
    private static String nullIfEmpty(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    record LocalMultipartUpload(@NonNull Path path) {
    }

    private record UploadSession(@NonNull Path path,
                                 @NonNull Map<String, String> metadata,
                                 @Nullable String contentType) {
    }

    private record StoredMultipartPart(@NonNull MultipartPart part,
                                       @NonNull Path path) {
    }

    private record LocalMultipartContinuationToken(@NonNull String key,
                                                   @NonNull String uploadId,
                                                   int pageSize,
                                                   int partNumberMarker) {

        private static LocalMultipartContinuationToken decode(String continuationToken) {
            if (!continuationToken.startsWith(MULTIPART_CONTINUATION_TOKEN_PREFIX)) {
                throw invalidMultipartContinuationToken();
            }
            String decodedToken = new String(
                Base64.getUrlDecoder().decode(continuationToken.substring(MULTIPART_CONTINUATION_TOKEN_PREFIX.length())),
                StandardCharsets.UTF_8
            );
            String[] parts = decodedToken.split("\n", -1);
            if (parts.length != 4) {
                throw invalidMultipartContinuationToken();
            }
            int partNumberMarker = Integer.parseInt(decodeTokenPart(parts[3]));
            if (partNumberMarker <= 0) {
                throw invalidMultipartContinuationToken();
            }
            return new LocalMultipartContinuationToken(
                decodeTokenPart(parts[0]),
                decodeTokenPart(parts[1]),
                Integer.parseInt(decodeTokenPart(parts[2])),
                partNumberMarker
            );
        }

        private boolean matches(ListMultipartPartsRequest request) {
            MultipartUploadHandle upload = request.getUpload();
            return upload.getKey().equals(key)
                && upload.getUploadId().equals(uploadId)
                && request.getPageSize() == pageSize;
        }
    }

    private static final class LocalCompletedMultipartUploadRequest extends FileUploadRequest {

        @NonNull
        private final Path path;

        private LocalCompletedMultipartUploadRequest(@NonNull String keyName,
                                                     @Nullable String contentType,
                                                     @NonNull Path path,
                                                     @NonNull Map<String, String> metadata) {
            super(keyName, contentType, path, metadata);
            this.path = path;
        }

        @Override
        @NonNull
        public InputStream getInputStream() {
            try {
                return LocalStorageIoSupport.newInputStreamNoFollow(path);
            } catch (IOException e) {
                throw new ObjectStorageException("Error reading completed multipart file: " + path, e);
            }
        }
    }
}
