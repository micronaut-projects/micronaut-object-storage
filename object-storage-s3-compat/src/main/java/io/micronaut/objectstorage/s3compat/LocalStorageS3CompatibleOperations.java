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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.local.LocalStorageEntry;
import io.micronaut.objectstorage.local.LocalStorageOperations;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Local storage bridge for the S3-compatible transport layer.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(S3CompatibilityConfiguration.class)
public class LocalStorageS3CompatibleOperations implements S3CompatibleOperations {

    private final S3CompatibilityConfiguration configuration;
    private final LocalStorageOperations operations;

    public LocalStorageS3CompatibleOperations(@Parameter S3CompatibilityConfiguration configuration,
                                              BeanContext beanContext) {
        this.configuration = configuration;
        this.operations = beanContext.getBean(LocalStorageOperations.class, Qualifiers.byName(configuration.getStorage()));
    }

    @Override
    @NonNull
    public Optional<S3Object> getObject(@NonNull String key) {
        return operations.retrieve(resolveStorageKey(key))
            .map(entry -> new S3Object(stripBasePath(entry.getKey()), entry, size(entry.getNativeEntry()), lastModified(entry.getNativeEntry())));
    }

    @Override
    @NonNull
    public UploadResponse<?> putObject(@NonNull String key,
                                       @NonNull InputStream inputStream,
                                       @Nullable Long contentLength,
                                       @Nullable String contentType) {
        try {
            UploadRequest request = UploadRequest.fromBytes(inputStream.readAllBytes(), resolveStorageKey(key));
            if (contentType != null && !contentType.isBlank()) {
                request.setContentType(contentType);
            }
            return operations.upload(request);
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading request body for S3-compatible upload", e);
        }
    }

    @Override
    public void deleteObject(@NonNull String key) {
        operations.delete(resolveStorageKey(key));
    }

    @Override
    @NonNull
    public S3ListResponse listObjects(@NonNull ListObjectsRequest request) {
        ListObjectsRequest storageRequest = new ListObjectsRequest(
            request.getPageSize(),
            resolveListPrefix(request.getPrefix().orElse(null)),
            request.getContinuationToken().map(this::resolveStorageKey).orElse(null)
        );
        io.micronaut.objectstorage.response.ListObjectsResponse response = operations.listObjects(storageRequest);
        List<S3ObjectSummary> objects = response.getKeys().stream()
            .map(this::toObjectSummary)
            .toList();
        return new S3ListResponse(
            objects,
            response.getContinuationToken().map(this::stripBasePath).orElse(null)
        );
    }

    @NonNull
    private S3ObjectSummary toObjectSummary(@NonNull String storageKey) {
        Path path = operations.retrieve(storageKey)
            .map(LocalStorageEntry::getNativeEntry)
            .orElse(null);
        return new S3ObjectSummary(
            stripBasePath(storageKey),
            path == null ? null : size(path),
            path == null ? null : lastModified(path)
        );
    }

    @NonNull
    private String resolveStorageKey(@NonNull String key) {
        String normalizedKey = normalizeKey(key);
        return configuration.getBasePath()
            .map(basePath -> basePath + normalizedKey)
            .orElse(normalizedKey);
    }

    @Nullable
    private String resolveListPrefix(@Nullable String prefix) {
        String normalizedPrefix = prefix == null ? null : normalizeKey(prefix);
        if (normalizedPrefix == null || normalizedPrefix.isEmpty()) {
            return configuration.getBasePath().orElse(null);
        }
        return configuration.getBasePath()
            .map(basePath -> basePath + normalizedPrefix)
            .orElse(normalizedPrefix);
    }

    @NonNull
    private String stripBasePath(@NonNull String key) {
        return configuration.getBasePath()
            .filter(key::startsWith)
            .map(basePath -> key.substring(basePath.length()))
            .orElse(key);
    }

    @NonNull
    private static String normalizeKey(@NonNull String key) {
        String normalized = key;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    @Nullable
    private static Long size(@NonNull Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading local object size: " + path, e);
        }
    }

    @Nullable
    private static Instant lastModified(@NonNull Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading local object timestamp: " + path, e);
        }
    }
}
