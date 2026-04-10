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

import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.local.LocalStorageEntry;
import io.micronaut.objectstorage.local.LocalStorageOperations;
import io.micronaut.objectstorage.request.ListObjectsRequest;
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
public final class LocalStorageS3CompatibleOperations extends AbstractS3CompatibleOperations {

    private final LocalStorageOperations operations;

    LocalStorageS3CompatibleOperations(@NonNull S3CompatibilityConfiguration configuration,
                                       @NonNull LocalStorageOperations operations) {
        super(configuration);
        this.operations = operations;
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
        return operations.upload(new S3CompatibilityUploadRequest(
            inputStream,
            resolveStorageKey(key),
            contentLength,
            contentType
        ));
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
