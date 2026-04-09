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
package io.micronaut.objectstorage.tus;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.request.FileUploadRequest;
import io.micronaut.objectstorage.local.LocalStorageConfiguration;
import io.micronaut.objectstorage.local.LocalStorageOperations;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Filesystem-backed tus upload backend for local storage.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(LocalStorageConfiguration.class)
@Singleton
@Internal
public class LocalTusUploadBackend implements TusUploadBackend {

    private static final String STATUS = "status";
    private static final String KEY = "key";
    private static final String LENGTH = "length";
    private static final String OFFSET = "offset";
    private static final String CONTENT_TYPE = "contentType";
    private static final String METADATA_PREFIX = "metadata.";

    private final String name;
    private final LocalStorageOperations operations;
    private final Path sessionsPath;
    private final Path stagingPath;

    public LocalTusUploadBackend(@Parameter String name,
                                 LocalStorageConfiguration configuration,
                                 LocalStorageOperations operations) {
        this.name = name;
        this.operations = operations;
        Path metadataRoot = configuration.getPath()
            .resolve(LocalStorageOperations.METADATA_DIRECTORY)
            .resolve("tus")
            .resolve(name);
        this.sessionsPath = metadataRoot.resolve("sessions");
        this.stagingPath = metadataRoot.resolve("staging");
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
        TusUpload upload = new TusUpload(uploadId, key, uploadLength, 0L, contentType, new LinkedHashMap<>(metadata), TusUploadStatus.IN_PROGRESS);
        save(upload);
        if (uploadLength == 0L) {
            return finalizeUpload(upload);
        }
        return upload;
    }

    @Override
    @NonNull
    public Optional<TusUpload> find(@NonNull String uploadId) {
        Path sessionFile = sessionFile(uploadId);
        if (!Files.exists(sessionFile)) {
            return Optional.empty();
        }
        return Optional.of(read(sessionFile));
    }

    @Override
    @NonNull
    public TusUpload append(@NonNull String uploadId, long expectedOffset, byte[] chunk) {
        TusUpload upload = find(uploadId).orElseThrow(() -> new IllegalArgumentException("Unknown upload: " + uploadId));
        if (upload.completed() || upload.aborted()) {
            throw new TusGoneException("Upload is no longer writable: " + uploadId);
        }
        if (expectedOffset != upload.offset()) {
            throw new TusConflictException("Upload-Offset does not match the committed offset");
        }
        long nextOffset = upload.offset() + chunk.length;
        if (nextOffset > upload.uploadLength()) {
            throw new IllegalArgumentException("Chunk exceeds declared Upload-Length");
        }

        Path stagingFile = stagingFile(uploadId);
        try {
            mkdirs(stagingFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(stagingFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
                outputStream.write(chunk);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist tus upload chunk", e);
        }

        TusUpload updated = new TusUpload(
            upload.id(),
            upload.key(),
            upload.uploadLength(),
            nextOffset,
            upload.getContentType().orElse(null),
            upload.metadata(),
            TusUploadStatus.IN_PROGRESS
        );
        save(updated);
        if (nextOffset == upload.uploadLength()) {
            return finalizeUpload(updated);
        }
        return updated;
    }

    @Override
    public void abort(@NonNull String uploadId) {
        TusUpload upload = find(uploadId).orElseThrow(() -> new IllegalArgumentException("Unknown upload: " + uploadId));
        if (upload.completed() || upload.aborted()) {
            throw new TusGoneException("Upload is no longer abortable: " + uploadId);
        }
        try {
            Files.deleteIfExists(stagingFile(uploadId));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete staged tus upload data", e);
        }
        save(new TusUpload(upload.id(), upload.key(), upload.uploadLength(), upload.offset(), upload.getContentType().orElse(null), upload.metadata(), TusUploadStatus.ABORTED));
    }

    private TusUpload finalizeUpload(TusUpload upload) {
        Path stagingFile = stagingFile(upload.id());
        if (!Files.exists(stagingFile)) {
            try {
                mkdirs(stagingFile.getParent());
                Files.createFile(stagingFile);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create staged tus upload file", e);
            }
        }
        FileUploadRequest uploadRequest = new FileUploadRequest(upload.key(), upload.getContentType().orElse(null), stagingFile, upload.metadata());
        operations.upload(uploadRequest);
        try {
            Files.deleteIfExists(stagingFile);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to clean staged tus upload data", e);
        }
        TusUpload completed = new TusUpload(upload.id(), upload.key(), upload.uploadLength(), upload.uploadLength(), upload.getContentType().orElse(null), upload.metadata(), TusUploadStatus.COMPLETED);
        save(completed);
        return completed;
    }

    private void save(TusUpload upload) {
        Properties properties = new Properties();
        properties.setProperty(KEY, upload.key());
        properties.setProperty(LENGTH, Long.toString(upload.uploadLength()));
        properties.setProperty(OFFSET, Long.toString(upload.offset()));
        properties.setProperty(STATUS, upload.status().name());
        upload.getContentType().ifPresent(contentType -> properties.setProperty(CONTENT_TYPE, contentType));
        upload.metadata().forEach((key, value) -> properties.setProperty(METADATA_PREFIX + key, value));
        Path sessionFile = sessionFile(upload.id());
        mkdirs(sessionFile.getParent());
        try (OutputStream outputStream = Files.newOutputStream(sessionFile)) {
            properties.store(outputStream, null);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist tus upload state", e);
        }
    }

    private TusUpload read(Path sessionFile) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(sessionFile)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read tus upload state", e);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(METADATA_PREFIX)) {
                metadata.put(propertyName.substring(METADATA_PREFIX.length()), properties.getProperty(propertyName));
            }
        }
        String uploadId = sessionFile.getFileName().toString().replaceFirst("\\.properties$", "");
        return new TusUpload(
            uploadId,
            properties.getProperty(KEY),
            Long.parseLong(properties.getProperty(LENGTH)),
            Long.parseLong(properties.getProperty(OFFSET)),
            properties.getProperty(CONTENT_TYPE),
            metadata,
            TusUploadStatus.valueOf(properties.getProperty(STATUS))
        );
    }

    private Path sessionFile(String uploadId) {
        return sessionsPath.resolve(uploadId + ".properties");
    }

    private Path stagingFile(String uploadId) {
        return stagingPath.resolve(uploadId + ".bin");
    }

    private static void mkdirs(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create tus storage path: " + path, e);
        }
    }
}
