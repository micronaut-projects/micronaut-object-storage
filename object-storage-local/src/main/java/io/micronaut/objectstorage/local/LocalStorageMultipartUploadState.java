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

import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.MultipartPart;
import io.micronaut.objectstorage.multipart.MultipartUploadHandle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

final class LocalStorageMultipartUploadState {

    private static final Logger LOG = LoggerFactory.getLogger(LocalStorageMultipartUploadState.class);

    private static final String MULTIPART_PARTS_DIRECTORY = "parts";
    private static final String MULTIPART_UPLOAD_PROPERTIES = "upload.properties";
    private static final String MULTIPART_COMPLETED_PROPERTIES = "completed.properties";
    private static final String MULTIPART_PART_EXTENSION = ".part";
    private static final String MULTIPART_PART_PROPERTIES_EXTENSION = ".properties";
    private static final String MULTIPART_COMPLETE_FILE_SUFFIX = ".complete";
    private static final String MULTIPART_KEY_PROPERTY = "key";
    private static final String MULTIPART_CONTENT_TYPE_PROPERTY = "contentType";
    private static final String MULTIPART_METADATA_PREFIX = "metadata.";
    private static final String MULTIPART_PART_ETAG_PROPERTY = "eTag";
    private static final String MULTIPART_PART_SIZE_PROPERTY = "size";
    private static final String MULTIPART_PART_FILE_PROPERTY = "file";

    private final LocalStorageLayout layout;
    private final boolean supportsPosixPermissions;

    LocalStorageMultipartUploadState(LocalStorageLayout layout) {
        this.layout = layout;
        this.supportsPosixPermissions = layout.storageRoot().getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @NonNull
    Path createUpload(@NonNull CreateMultipartUploadRequest request, @NonNull String uploadId) {
        layout.objectPath(request.getKey());
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
            return uploadPath;
        } catch (RuntimeException e) {
            deleteRecursively(uploadPath, e);
            throw e;
        }
    }

    @NonNull
    UploadSession retrieveUploadSession(@NonNull MultipartUploadHandle upload) {
        Path uploadPath = multipartUploadPath(upload.getUploadId());
        Path completedPropertiesPath = uploadPath.resolve(MULTIPART_COMPLETED_PROPERTIES);
        if (Files.exists(completedPropertiesPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ObjectStorageException("Local multipart upload is already completed: " + upload.getUploadId());
        }
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
    StoredMultipartPart storePart(@NonNull Path uploadPath, int partNumber, @NonNull InputStream inputStream) {
        Path partsPath = multipartPartsPath(uploadPath);
        Path partPropertiesFile = multipartPartPropertiesPath(uploadPath, partNumber);
        String previousPartFileName = retrieveCurrentPartFileName(uploadPath, partPropertiesFile, partNumber).orElse(null);
        if (!mkdirs(partsPath)) {
            throw new ObjectStorageException("Error creating local multipart part directories: " + partsPath);
        }
        String eTag = UUID.randomUUID().toString();
        String partFileName = partNumber + "-" + eTag + MULTIPART_PART_EXTENSION;
        Path partFile = multipartPartDataPath(uploadPath, partFileName);
        Path temporaryPartFile = null;
        Path temporaryPartPropertiesFile = null;
        boolean partFileCommitted = false;
        boolean propertiesCommitted = false;
        try {
            temporaryPartFile = createTempFile(partsPath, partNumber + "-", MULTIPART_PART_EXTENSION + ".tmp");
            temporaryPartPropertiesFile = createTempFile(partsPath, partNumber + "-", MULTIPART_PART_PROPERTIES_EXTENSION + ".tmp");
            storeFile(temporaryPartFile, inputStream);
            long partSize = size(temporaryPartFile);
            Properties properties = new Properties();
            properties.setProperty(MULTIPART_PART_ETAG_PROPERTY, eTag);
            properties.setProperty(MULTIPART_PART_SIZE_PROPERTY, Long.toString(partSize));
            properties.setProperty(MULTIPART_PART_FILE_PROPERTY, partFileName);
            storeRequiredProperties(temporaryPartPropertiesFile, properties);
            moveReplacing(temporaryPartFile, partFile);
            partFileCommitted = true;
            moveReplacing(temporaryPartPropertiesFile, partPropertiesFile);
            propertiesCommitted = true;
            deleteStalePartFile(uploadPath, previousPartFileName, partFileName);
            return new StoredMultipartPart(new MultipartPart(partNumber, eTag, partSize), partFile);
        } catch (RuntimeException e) {
            deleteIfExists(temporaryPartFile, e);
            deleteIfExists(temporaryPartPropertiesFile, e);
            if (partFileCommitted && !propertiesCommitted) {
                deleteIfExists(partFile, e);
            }
            throw e;
        }
    }

    @NonNull
    List<MultipartPart> retrieveMultipartParts(@NonNull Path uploadPath) {
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
    Path buildCompletedMultipartFile(@NonNull Path uploadPath,
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

    Path multipartUploadPath(String uploadId) {
        try {
            UUID.fromString(uploadId);
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid local multipart upload id: " + uploadId, e);
        }
        Path uploadPath = layout.multipartBucketDirectory().resolve(uploadId);
        LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), uploadPath);
        return uploadPath;
    }

    Optional<Properties> retrieveAbortProperties(Path uploadPath) {
        Optional<Properties> activeProperties = retrievePropertiesIfExists(uploadPath.resolve(MULTIPART_UPLOAD_PROPERTIES));
        if (activeProperties.isPresent()) {
            return activeProperties;
        }
        return retrievePropertiesIfExists(uploadPath.resolve(MULTIPART_COMPLETED_PROPERTIES));
    }

    void validateUploadSessionKey(MultipartUploadHandle upload, Properties properties) {
        String key = properties.getProperty(MULTIPART_KEY_PROPERTY);
        if (!upload.getKey().equals(key)) {
            throw new ObjectStorageException("Multipart upload key does not match local upload session");
        }
    }

    void markMultipartUploadCompleted(Path uploadPath) {
        moveReplacing(
            uploadPath.resolve(MULTIPART_UPLOAD_PROPERTIES),
            uploadPath.resolve(MULTIPART_COMPLETED_PROPERTIES)
        );
    }

    void deleteMultipartUpload(Path uploadPath) {
        try {
            LocalStorageBucketOperations.deleteRecursively(uploadPath);
        } catch (NoSuchFileException ignored) {
            // Abort must be safe to retry, and completed uploads have already removed their local session.
        } catch (IOException e) {
            throw new ObjectStorageException("Error deleting local multipart upload: " + uploadPath, e);
        }
        deleteEmptyMultipartDirectories(null);
    }

    void deleteMultipartUploadAfterCompletion(Path uploadPath) {
        try {
            deleteCompletedMultipartUpload(uploadPath);
        } catch (RuntimeException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error deleting completed local multipart upload: {}", uploadPath, e);
            }
        }
    }

    void deleteIfExists(@Nullable Path path, Throwable failure) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            failure.addSuppressed(e);
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

    private Optional<String> retrieveCurrentPartFileName(Path uploadPath, Path propertiesPath, int partNumber) {
        if (!Files.exists(propertiesPath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        String partFileName = retrieveRequiredProperties(propertiesPath).getProperty(MULTIPART_PART_FILE_PROPERTY);
        if (partFileName == null) {
            throw new ObjectStorageException("Local multipart part metadata is incomplete: " + partNumber);
        }
        multipartPartDataPath(uploadPath, partFileName);
        return Optional.of(partFileName);
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
        try (InputStream in = inputStream) {
            if (!mkdirs(file.getParent())) {
                throw new ObjectStorageException("Error creating local multipart part directories: " + file);
            }
            try (OutputStream fileOut = LocalStorageIoSupport.newOutputStreamNoFollow(file, supportsPosixPermissions)) {
                in.transferTo(fileOut);
            }
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

    private Optional<Properties> retrievePropertiesIfExists(Path propertiesPath) {
        Properties properties = new Properties();
        try (InputStream in = LocalStorageIoSupport.newInputStreamNoFollow(propertiesPath)) {
            properties.load(in);
            return Optional.of(properties);
        } catch (NoSuchFileException e) {
            return Optional.empty();
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

    private void deleteCompletedMultipartUpload(Path uploadPath) {
        try {
            Path completedPropertiesPath = uploadPath.resolve(MULTIPART_COMPLETED_PROPERTIES);
            try (Stream<Path> stream = Files.list(uploadPath)) {
                for (Path path : stream.filter(path -> !path.equals(completedPropertiesPath)).toList()) {
                    deleteRecursivelyOrFile(path);
                }
            }
            Files.deleteIfExists(completedPropertiesPath);
            Files.deleteIfExists(uploadPath);
        } catch (NoSuchFileException ignored) {
            // A concurrent abort or cleanup may have already removed the completed session.
        } catch (IOException e) {
            throw new ObjectStorageException("Error deleting local multipart upload: " + uploadPath, e);
        }
        deleteEmptyMultipartDirectories(null);
    }

    private void deleteRecursivelyOrFile(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            LocalStorageBucketOperations.deleteRecursively(path);
        } else {
            Files.delete(path);
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

    record UploadSession(@NonNull Path path,
                         @NonNull Map<String, String> metadata,
                         @Nullable String contentType) {
    }

    record StoredMultipartPart(@NonNull MultipartPart part,
                               @NonNull Path path) {
    }
}
