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
package io.micronaut.objectstorage.metadata.jdbc.repository;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.metadata.ObjectStorageOperationType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outbox entry persisted for asynchronous metadata reconciliation.
 */
@Internal
public final class StorageMetadataOutboxRecord {

    private final String id;
    private final String tenantId;
    private final String storageName;
    private final String logicalContainer;
    @Nullable
    private final String objectKey;
    @Nullable
    private final String objectKeyHash;
    @Nullable
    private final String destinationObjectKey;
    @Nullable
    private final String destinationObjectKeyHash;
    private final ObjectStorageOperationType operationType;
    @Nullable
    private final String reconciliationId;
    private final int payloadVersion;
    private final StorageMetadataOutboxStatus status;
    private final int attemptCount;
    private final Instant nextAttemptAt;
    @Nullable
    private final String lastErrorSummary;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final Instant updatedAt;

    private StorageMetadataOutboxRecord(@NonNull Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.storageName = Objects.requireNonNull(builder.storageName, "storageName");
        this.logicalContainer = Objects.requireNonNull(builder.logicalContainer, "logicalContainer");
        this.objectKey = builder.objectKey;
        this.objectKeyHash = builder.objectKeyHash;
        this.destinationObjectKey = builder.destinationObjectKey;
        this.destinationObjectKeyHash = builder.destinationObjectKeyHash;
        this.operationType = Objects.requireNonNull(builder.operationType, "operationType");
        this.reconciliationId = builder.reconciliationId;
        this.payloadVersion = builder.payloadVersion;
        this.status = Objects.requireNonNull(builder.status, "status");
        this.attemptCount = builder.attemptCount;
        this.nextAttemptAt = Objects.requireNonNull(builder.nextAttemptAt, "nextAttemptAt");
        this.lastErrorSummary = builder.lastErrorSummary;
        this.idempotencyKey = Objects.requireNonNull(builder.idempotencyKey, "idempotencyKey");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt");
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Deprecated(forRemoval = true)
    public StorageMetadataOutboxRecord(@NonNull String id,
                                       @NonNull String tenantId,
                                       @NonNull String storageName,
                                       @NonNull String logicalContainer,
                                       @Nullable String objectKey,
                                       @Nullable String objectKeyHash,
                                       @Nullable String destinationObjectKey,
                                       @Nullable String destinationObjectKeyHash,
                                       @NonNull ObjectStorageOperationType operationType,
                                       @Nullable String reconciliationId,
                                       int payloadVersion,
                                       @NonNull StorageMetadataOutboxStatus status,
                                       int attemptCount,
                                       @NonNull Instant nextAttemptAt,
                                       @Nullable String lastErrorSummary,
                                       @NonNull String idempotencyKey,
                                       @NonNull Instant createdAt,
                                       @NonNull Instant updatedAt) {
        this(builder()
            .id(id)
            .tenantId(tenantId)
            .storageName(storageName)
            .logicalContainer(logicalContainer)
            .objectKey(objectKey)
            .objectKeyHash(objectKeyHash)
            .destinationObjectKey(destinationObjectKey)
            .destinationObjectKeyHash(destinationObjectKeyHash)
            .operationType(operationType)
            .reconciliationId(reconciliationId)
            .payloadVersion(payloadVersion)
            .status(status)
            .attemptCount(attemptCount)
            .nextAttemptAt(nextAttemptAt)
            .lastErrorSummary(lastErrorSummary)
            .idempotencyKey(idempotencyKey)
            .createdAt(createdAt)
            .updatedAt(updatedAt));
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTenantId() {
        return tenantId;
    }

    @NonNull
    public String getStorageName() {
        return storageName;
    }

    @NonNull
    public String getLogicalContainer() {
        return logicalContainer;
    }

    @NonNull
    public Optional<String> getObjectKey() {
        return Optional.ofNullable(objectKey);
    }

    @NonNull
    public Optional<String> getObjectKeyHash() {
        return Optional.ofNullable(objectKeyHash);
    }

    @NonNull
    public Optional<String> getDestinationObjectKey() {
        return Optional.ofNullable(destinationObjectKey);
    }

    @NonNull
    public Optional<String> getDestinationObjectKeyHash() {
        return Optional.ofNullable(destinationObjectKeyHash);
    }

    @NonNull
    public ObjectStorageOperationType getOperationType() {
        return operationType;
    }

    @NonNull
    public Optional<String> getReconciliationId() {
        return Optional.ofNullable(reconciliationId);
    }

    public int getPayloadVersion() {
        return payloadVersion;
    }

    @NonNull
    public StorageMetadataOutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    @NonNull
    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    @NonNull
    public Optional<String> getLastErrorSummary() {
        return Optional.ofNullable(lastErrorSummary);
    }

    @NonNull
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @NonNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Mutable builder used to create immutable outbox records.
     */
    public static final class Builder {
        private String id;
        private String tenantId;
        private String storageName;
        private String logicalContainer;
        @Nullable
        private String objectKey;
        @Nullable
        private String objectKeyHash;
        @Nullable
        private String destinationObjectKey;
        @Nullable
        private String destinationObjectKeyHash;
        private ObjectStorageOperationType operationType;
        @Nullable
        private String reconciliationId;
        private int payloadVersion;
        private StorageMetadataOutboxStatus status;
        private int attemptCount;
        private Instant nextAttemptAt;
        @Nullable
        private String lastErrorSummary;
        private String idempotencyKey;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {
        }

        @NonNull
        public Builder id(@NonNull String id) {
            this.id = id;
            return this;
        }

        @NonNull
        public Builder tenantId(@NonNull String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        @NonNull
        public Builder storageName(@NonNull String storageName) {
            this.storageName = storageName;
            return this;
        }

        @NonNull
        public Builder logicalContainer(@NonNull String logicalContainer) {
            this.logicalContainer = logicalContainer;
            return this;
        }

        @NonNull
        public Builder objectKey(@Nullable String objectKey) {
            this.objectKey = objectKey;
            return this;
        }

        @NonNull
        public Builder objectKeyHash(@Nullable String objectKeyHash) {
            this.objectKeyHash = objectKeyHash;
            return this;
        }

        @NonNull
        public Builder destinationObjectKey(@Nullable String destinationObjectKey) {
            this.destinationObjectKey = destinationObjectKey;
            return this;
        }

        @NonNull
        public Builder destinationObjectKeyHash(@Nullable String destinationObjectKeyHash) {
            this.destinationObjectKeyHash = destinationObjectKeyHash;
            return this;
        }

        @NonNull
        public Builder operationType(@NonNull ObjectStorageOperationType operationType) {
            this.operationType = operationType;
            return this;
        }

        @NonNull
        public Builder reconciliationId(@Nullable String reconciliationId) {
            this.reconciliationId = reconciliationId;
            return this;
        }

        @NonNull
        public Builder payloadVersion(int payloadVersion) {
            this.payloadVersion = payloadVersion;
            return this;
        }

        @NonNull
        public Builder status(@NonNull StorageMetadataOutboxStatus status) {
            this.status = status;
            return this;
        }

        @NonNull
        public Builder attemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        @NonNull
        public Builder nextAttemptAt(@NonNull Instant nextAttemptAt) {
            this.nextAttemptAt = nextAttemptAt;
            return this;
        }

        @NonNull
        public Builder lastErrorSummary(@Nullable String lastErrorSummary) {
            this.lastErrorSummary = lastErrorSummary;
            return this;
        }

        @NonNull
        public Builder idempotencyKey(@NonNull String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        @NonNull
        public Builder createdAt(@NonNull Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        @NonNull
        public Builder updatedAt(@NonNull Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        @NonNull
        public StorageMetadataOutboxRecord build() {
            return new StorageMetadataOutboxRecord(this);
        }
    }
}
