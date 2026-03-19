/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.metadata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Metadata snapshot for a single object in logical storage.
 */
public final class StorageObjectMetadata {

    private final String tenantId;
    private final StorageDescriptor storageDescriptor;
    private final String objectKey;
    private final String objectKeyHash;
    @Nullable
    private final String contentType;
    @Nullable
    private final Long contentLength;
    @Nullable
    private final String etag;
    @Nullable
    private final String providerVersionId;
    @Nullable
    private final String checksum;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final ObjectMetadataSyncStatus syncStatus;

    public StorageObjectMetadata(@NonNull String tenantId,
                                 @NonNull StorageDescriptor storageDescriptor,
                                 @NonNull String objectKey,
                                 @Nullable String objectKeyHash,
                                 @Nullable String contentType,
                                 @Nullable Long contentLength,
                                 @Nullable String etag,
                                 @Nullable String providerVersionId,
                                 @Nullable String checksum,
                                 @NonNull Instant createdAt,
                                 @NonNull Instant updatedAt,
                                 @NonNull ObjectMetadataSyncStatus syncStatus) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.storageDescriptor = Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.objectKeyHash = normalizeObjectKeyHash(objectKey, objectKeyHash);
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.etag = etag;
        this.providerVersionId = providerVersionId;
        this.checksum = checksum;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.syncStatus = Objects.requireNonNull(syncStatus, "syncStatus");
    }

    @NonNull
    public String getTenantId() {
        return tenantId;
    }

    @NonNull
    public String getStorageName() {
        return storageDescriptor.getStorageName();
    }

    @NonNull
    public String getProviderId() {
        return storageDescriptor.getProviderId();
    }

    @NonNull
    public String getLogicalContainer() {
        return storageDescriptor.getLogicalContainer();
    }

    @NonNull
    public Optional<String> getProviderNamespace() {
        return storageDescriptor.getProviderNamespace();
    }

    @NonNull
    public String getProviderContainer() {
        return storageDescriptor.getProviderContainer();
    }

    @NonNull
    public String getProviderContainerIdentity() {
        return storageDescriptor.getProviderContainerIdentity();
    }

    @NonNull
    public StorageDescriptor getStorageDescriptor() {
        return storageDescriptor;
    }

    @NonNull
    public String getObjectKey() {
        return objectKey;
    }

    @NonNull
    public String getObjectKeyHash() {
        return objectKeyHash;
    }

    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @NonNull
    public OptionalLong getContentLength() {
        if (contentLength == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(contentLength);
    }

    @NonNull
    public Optional<String> getEtag() {
        return Optional.ofNullable(etag);
    }

    @NonNull
    public Optional<String> getProviderVersionId() {
        return Optional.ofNullable(providerVersionId);
    }

    @NonNull
    public Optional<String> getChecksum() {
        return Optional.ofNullable(checksum);
    }

    @NonNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public ObjectMetadataSyncStatus getSyncStatus() {
        return syncStatus;
    }

    @NonNull
    public ObjectMetadataReconciliationState getReconciliationStatus() {
        return syncStatus.getReconciliationState();
    }

    @NonNull
    public Optional<String> getLastErrorSummary() {
        return syncStatus.getLastErrorSummary();
    }

    @NonNull
    public static String deterministicObjectKeyHash(@NonNull String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
        byte[] digest = messageDigest.digest(objectKey.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    @NonNull
    private static String normalizeObjectKeyHash(@NonNull String objectKey, @Nullable String objectKeyHash) {
        if (objectKeyHash == null || objectKeyHash.isBlank()) {
            return deterministicObjectKeyHash(objectKey);
        }
        return objectKeyHash;
    }
}
