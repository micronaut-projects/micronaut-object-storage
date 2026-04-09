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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.tus.TusConflictException;
import io.micronaut.objectstorage.tus.TusGoneException;
import io.micronaut.objectstorage.tus.TusModuleConfiguration;
import io.micronaut.objectstorage.tus.TusUpload;
import io.micronaut.objectstorage.tus.TusUploadBackend;
import io.micronaut.objectstorage.tus.TusUploadStatus;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * AWS S3 multipart-backed tus upload backend.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(AwsS3Configuration.class)
@Requires(beans = TusModuleConfiguration.class)
@Requires(property = TusModuleConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE)
@Singleton
@Internal
final class AwsS3TusUploadBackend implements TusUploadBackend {

    private static final System.Logger LOGGER = System.getLogger(AwsS3TusUploadBackend.class.getName());
    private static final long MIN_PART_SIZE = 5L * 1024L * 1024L;
    private static final String STATUS = "status";
    private static final String KEY = "key";
    private static final String LENGTH = "length";
    private static final String OFFSET = "offset";
    private static final String CONTENT_TYPE = "contentType";
    private static final String MULTIPART_UPLOAD_ID = "aws.multipartUploadId";
    private static final String REMOTE_OFFSET = "aws.remoteOffset";
    private static final String NEXT_PART_NUMBER = "aws.nextPartNumber";
    private static final String PART_PREFIX = "aws.part.";
    private static final String METADATA_PREFIX = "metadata.";

    private final String name;
    private final AwsS3Configuration configuration;
    private final S3Client s3Client;
    private final Path sessionsPath;
    private final Path stagingPath;
    private final ConcurrentMap<String, ReentrantLock> uploadLocks = new ConcurrentHashMap<>();

    AwsS3TusUploadBackend(@Parameter String name,
                          AwsS3Configuration configuration,
                          TusModuleConfiguration tusModuleConfiguration,
                          S3Client s3Client) {
        this.name = name;
        this.configuration = configuration;
        this.s3Client = s3Client;
        Path rootPath = tusModuleConfiguration.getStorageDirectory()
            .resolve("aws")
            .resolve(name);
        this.sessionsPath = rootPath.resolve("sessions");
        this.stagingPath = rootPath.resolve("staging");
        mkdirs(this.sessionsPath);
        mkdirs(this.stagingPath);
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public TusUpload create(@NonNull String key,
                            long uploadLength,
                            @Nullable String contentType,
                            @NonNull Map<String, String> metadata) {
        String uploadId = UUID.randomUUID().toString();
        TusUpload upload = new TusUpload(
            uploadId,
            key,
            uploadLength,
            0L,
            contentType,
            new LinkedHashMap<>(metadata),
            TusUploadStatus.IN_PROGRESS
        );
        if (uploadLength == 0L) {
            putEmptyObject(upload);
            AwsTusSession session = new AwsTusSession(upload.withStatus(TusUploadStatus.COMPLETED, upload.uploadLength()), null, 0L, 1, List.of());
            save(session);
            return session.upload();
        }

        try {
            CreateMultipartUploadRequest.Builder requestBuilder = CreateMultipartUploadRequest.builder()
                .bucket(configuration.getBucket())
                .key(key);
            if (contentType != null) {
                requestBuilder.contentType(contentType);
            }
            if (!metadata.isEmpty()) {
                requestBuilder.metadata(metadata);
            }
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(requestBuilder.build());
            AwsTusSession session = new AwsTusSession(upload, response.uploadId(), 0L, 1, List.of());
            try {
                save(session);
            } catch (RuntimeException e) {
                abortMultipartUploadAfterCreateFailure(key, response.uploadId(), e);
            }
            return upload;
        } catch (AwsServiceException e) {
            throw new ObjectStorageException("Unable to create AWS S3 multipart upload for tus resource", e);
        }
    }

    @Override
    @NonNull
    public Optional<TusUpload> find(@NonNull String uploadId) {
        Path sessionFile = sessionFile(uploadId);
        if (!Files.exists(sessionFile)) {
            return Optional.empty();
        }
        return Optional.of(read(sessionFile).upload());
    }

    @Override
    @NonNull
    public TusUpload append(@NonNull String uploadId, long expectedOffset, byte[] chunk) {
        return withUploadLock(uploadId, () -> {
            AwsTusSession session = readRequired(uploadId);
            TusUpload upload = session.upload();
            if (!upload.inProgress()) {
                throw new TusGoneException("Upload is no longer writable: " + uploadId);
            }
            if (expectedOffset != upload.offset()) {
                throw new TusConflictException("Upload-Offset does not match the committed offset");
            }
            long nextOffset = upload.offset() + chunk.length;
            if (nextOffset > upload.uploadLength()) {
                throw new IllegalArgumentException("Chunk exceeds declared Upload-Length");
            }

            appendToStaging(uploadId, chunk);
            session = session.withUpload(upload.withStatus(upload.status(), nextOffset));
            save(session);

            if (nextOffset == upload.uploadLength()) {
                return finalizeUpload(session).upload();
            }
            return flushReadyParts(session).upload();
        });
    }

    @Override
    public void abort(@NonNull String uploadId) {
        withUploadLock(uploadId, () -> {
            AwsTusSession session = readRequired(uploadId);
            if (!session.upload().inProgress()) {
                throw new TusGoneException("Upload is no longer abortable: " + uploadId);
            }
            if (session.multipartUploadId() != null) {
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(configuration.getBucket())
                        .key(session.upload().key())
                        .uploadId(session.multipartUploadId())
                        .build());
                } catch (S3Exception e) {
                    throw new ObjectStorageException("Unable to abort AWS S3 multipart upload for tus resource", e);
                }
            }
            save(session.withUpload(session.upload().withStatus(TusUploadStatus.ABORTED, session.upload().offset())));
            deleteQuietly(stagingFile(uploadId));
            return null;
        });
    }

    private AwsTusSession finalizeUpload(AwsTusSession session) {
        session = flushReadyParts(session);
        long pendingBytes = pendingBytes(session);
        if (pendingBytes > 0L) {
            session = uploadPart(session, pendingBytes);
        }
        if (session.multipartUploadId() != null) {
            try {
                s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(configuration.getBucket())
                    .key(session.upload().key())
                    .uploadId(session.multipartUploadId())
                    .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(session.completedParts())
                        .build())
                    .build());
            } catch (AwsServiceException e) {
                throw new ObjectStorageException("Unable to complete AWS S3 multipart upload for tus resource", e);
            }
        }
        AwsTusSession completed = session.withUpload(session.upload().withStatus(TusUploadStatus.COMPLETED, session.upload().uploadLength()));
        save(completed);
        deleteQuietly(stagingFile(session.upload().id()));
        return completed;
    }

    private AwsTusSession flushReadyParts(AwsTusSession session) {
        AwsTusSession current = session;
        while (pendingBytes(current) >= MIN_PART_SIZE) {
            current = uploadPart(current, MIN_PART_SIZE);
        }
        return current;
    }

    private AwsTusSession uploadPart(AwsTusSession session, long partSize) {
        Path stagingFile = stagingFile(session.upload().id());
        byte[] bytes = readBytes(stagingFile, 0L, partSize);
        try {
            UploadPartResponse response = s3Client.uploadPart(
                UploadPartRequest.builder()
                    .bucket(configuration.getBucket())
                    .key(session.upload().key())
                    .uploadId(session.multipartUploadId())
                    .partNumber(session.nextPartNumber())
                    .contentLength((long) bytes.length)
                    .build(),
                RequestBody.fromBytes(bytes)
            );
            List<CompletedPart> completedParts = new ArrayList<>(session.completedParts());
            completedParts.add(CompletedPart.builder()
                .partNumber(session.nextPartNumber())
                .eTag(response.eTag())
                .build());
            AwsTusSession updated = new AwsTusSession(
                session.upload(),
                session.multipartUploadId(),
                session.uploadedOffset() + bytes.length,
                session.nextPartNumber() + 1,
                completedParts
            );
            Path backupFile = compactStagingPrefix(stagingFile, bytes.length);
            try {
                save(updated);
            } catch (RuntimeException e) {
                restoreCompactedStaging(stagingFile, backupFile, e);
            }
            deleteBackupQuietly(backupFile);
            return updated;
        } catch (AwsServiceException e) {
            throw new ObjectStorageException("Unable to upload AWS S3 multipart tus chunk", e);
        }
    }

    private AwsTusSession readRequired(String uploadId) {
        Path sessionFile = sessionFile(uploadId);
        if (!Files.exists(sessionFile)) {
            throw new IllegalArgumentException("Unknown upload: " + uploadId);
        }
        return read(sessionFile);
    }

    private void appendToStaging(String uploadId, byte[] chunk) {
        Path stagingFile = stagingFile(uploadId);
        mkdirs(stagingFile.getParent());
        try (OutputStream outputStream = Files.newOutputStream(
            stagingFile,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )) {
            outputStream.write(chunk);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist AWS tus upload chunk", e);
        }
    }

    private void putEmptyObject(TusUpload upload) {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(upload.key());
            upload.getContentType().ifPresent(requestBuilder::contentType);
            if (!upload.metadata().isEmpty()) {
                requestBuilder.metadata(upload.metadata());
            }
            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(new byte[0]));
        } catch (AwsServiceException e) {
            throw new ObjectStorageException("Unable to create empty AWS S3 object for tus upload", e);
        }
    }

    private void save(AwsTusSession session) {
        Properties properties = new Properties();
        properties.setProperty(KEY, session.upload().key());
        properties.setProperty(LENGTH, Long.toString(session.upload().uploadLength()));
        properties.setProperty(OFFSET, Long.toString(session.upload().offset()));
        properties.setProperty(STATUS, session.upload().status().name());
        properties.setProperty(REMOTE_OFFSET, Long.toString(session.uploadedOffset()));
        properties.setProperty(NEXT_PART_NUMBER, Integer.toString(session.nextPartNumber()));
        if (session.multipartUploadId() != null) {
            properties.setProperty(MULTIPART_UPLOAD_ID, session.multipartUploadId());
        }
        session.upload().getContentType().ifPresent(contentType -> properties.setProperty(CONTENT_TYPE, contentType));
        session.upload().metadata().forEach((key, value) -> properties.setProperty(METADATA_PREFIX + key, value));
        session.completedParts().forEach(part -> properties.setProperty(PART_PREFIX + part.partNumber(), part.eTag()));

        Path sessionFile = sessionFile(session.upload().id());
        mkdirs(sessionFile.getParent());
        try (OutputStream outputStream = Files.newOutputStream(sessionFile)) {
            properties.store(outputStream, null);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist AWS tus upload state", e);
        }
    }

    private AwsTusSession read(Path sessionFile) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(sessionFile)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read AWS tus upload state", e);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        List<CompletedPart> completedParts = new ArrayList<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(METADATA_PREFIX)) {
                metadata.put(propertyName.substring(METADATA_PREFIX.length()), properties.getProperty(propertyName));
            } else if (propertyName.startsWith(PART_PREFIX)) {
                int partNumber = Integer.parseInt(propertyName.substring(PART_PREFIX.length()));
                completedParts.add(CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(properties.getProperty(propertyName))
                    .build());
            }
        }
        completedParts.sort(Comparator.comparingInt(CompletedPart::partNumber));

        String uploadId = sessionFile.getFileName().toString().replaceFirst("\\.properties$", "");
        TusUpload upload = new TusUpload(
            uploadId,
            properties.getProperty(KEY),
            Long.parseLong(properties.getProperty(LENGTH)),
            Long.parseLong(properties.getProperty(OFFSET)),
            properties.getProperty(CONTENT_TYPE),
            metadata,
            TusUploadStatus.valueOf(properties.getProperty(STATUS))
        );
        return new AwsTusSession(
            upload,
            properties.getProperty(MULTIPART_UPLOAD_ID),
            Long.parseLong(properties.getProperty(REMOTE_OFFSET, "0")),
            Integer.parseInt(properties.getProperty(NEXT_PART_NUMBER, "1")),
            completedParts
        );
    }

    private Path sessionFile(String uploadId) {
        return sessionsPath.resolve(uploadId + ".properties");
    }

    private Path stagingFile(String uploadId) {
        return stagingPath.resolve(uploadId + ".bin");
    }

    private long pendingBytes(AwsTusSession session) {
        return session.upload().offset() - session.uploadedOffset();
    }

    private static byte[] readBytes(Path file, long start, long length) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "r")) {
            randomAccessFile.seek(start);
            byte[] bytes = new byte[Math.toIntExact(length)];
            randomAccessFile.readFully(bytes);
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read staged AWS tus upload data", e);
        }
    }

    private <T> T withUploadLock(String uploadId, Supplier<T> supplier) {
        ReentrantLock lock = uploadLocks.computeIfAbsent(uploadId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                uploadLocks.remove(uploadId, lock);
            }
        }
    }

    private void abortMultipartUploadAfterCreateFailure(String key, String multipartUploadId, RuntimeException original) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .uploadId(multipartUploadId)
                .build());
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
        throw original;
    }

    private static Path compactStagingPrefix(Path stagingFile, long bytesToDiscard) {
        if (bytesToDiscard <= 0L || !Files.exists(stagingFile)) {
            return null;
        }
        Path tempFile = stagingFile.resolveSibling(stagingFile.getFileName() + ".tmp");
        Path backupFile = stagingFile.resolveSibling(stagingFile.getFileName() + ".bak");
        try (InputStream inputStream = Files.newInputStream(stagingFile);
             OutputStream outputStream = Files.newOutputStream(tempFile)) {
            inputStream.skipNBytes(bytesToDiscard);
            inputStream.transferTo(outputStream);
            moveReplacing(stagingFile, backupFile);
            try {
                moveReplacing(tempFile, stagingFile);
            } catch (IOException e) {
                moveReplacing(backupFile, stagingFile);
                throw e;
            }
            return backupFile;
        } catch (IOException e) {
            deleteIfExistsQuietly(tempFile);
            deleteIfExistsQuietly(backupFile);
            throw new IllegalStateException("Unable to compact staged AWS tus upload data", e);
        }
    }

    private static void restoreCompactedStaging(Path stagingFile, @Nullable Path backupFile, RuntimeException original) {
        if (backupFile == null || !Files.exists(backupFile)) {
            throw original;
        }
        try {
            moveReplacing(backupFile, stagingFile);
        } catch (IOException restoreFailure) {
            original.addSuppressed(restoreFailure);
        }
        throw original;
    }

    private static void deleteBackupQuietly(@Nullable Path backupFile) {
        if (backupFile != null) {
            deleteIfExistsQuietly(backupFile);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Unable to clean staged AWS tus upload data: " + path,
                e);
        }
    }

    private static void deleteIfExistsQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void mkdirs(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create tus storage path: " + path, e);
        }
    }

    private record AwsTusSession(
        TusUpload upload,
        @Nullable String multipartUploadId,
        long uploadedOffset,
        int nextPartNumber,
        List<CompletedPart> completedParts
    ) {
        private AwsTusSession withUpload(TusUpload upload) {
            return new AwsTusSession(upload, multipartUploadId, uploadedOffset, nextPartNumber, completedParts);
        }
    }
}
