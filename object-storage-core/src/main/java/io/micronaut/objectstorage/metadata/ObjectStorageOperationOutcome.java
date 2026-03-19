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

import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Successful delegate storage outcome captured for metadata workflows.
 *
 * @since 1.6.0
 */
public final class ObjectStorageOperationOutcome {

    private final ObjectStorageOperationType operationType;
    private final String tenantId;
    private final StorageDescriptor storageDescriptor;
    private final String objectKey;
    @Nullable
    private final String destinationObjectKey;
    @Nullable
    private final String reconciliationId;
    @Nullable
    private final UploadResponse<?> uploadResponse;
    @Nullable
    private final Object delegateResponse;

    public ObjectStorageOperationOutcome(@NonNull ObjectStorageOperationType operationType,
                                         @NonNull String tenantId,
                                         @NonNull StorageDescriptor storageDescriptor,
                                         @NonNull String objectKey,
                                         @Nullable String destinationObjectKey,
                                         @Nullable String reconciliationId,
                                         @Nullable UploadResponse<?> uploadResponse,
                                         @Nullable Object delegateResponse) {
        this.operationType = Objects.requireNonNull(operationType, "operationType");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.storageDescriptor = Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.destinationObjectKey = destinationObjectKey;
        this.reconciliationId = reconciliationId;
        this.uploadResponse = uploadResponse;
        this.delegateResponse = delegateResponse;
    }

    @NonNull
    public static ObjectStorageOperationOutcome uploadSuccess(@NonNull ObjectStorageOperationContext context,
                                                              @NonNull UploadResponse<?> uploadResponse,
                                                              @Nullable String reconciliationId) {
        Objects.requireNonNull(context, "context");
        return new ObjectStorageOperationOutcome(
            ObjectStorageOperationType.UPLOAD,
            context.getTenantId(),
            context.getStorageDescriptor(),
            context.getObjectKey(),
            null,
            reconciliationId,
            Objects.requireNonNull(uploadResponse, "uploadResponse"),
            null
        );
    }

    @NonNull
    public static ObjectStorageOperationOutcome copySuccess(@NonNull ObjectStorageOperationContext context,
                                                            @Nullable Object delegateResponse,
                                                            @Nullable String reconciliationId) {
        Objects.requireNonNull(context, "context");
        return new ObjectStorageOperationOutcome(
            ObjectStorageOperationType.COPY,
            context.getTenantId(),
            context.getStorageDescriptor(),
            context.getObjectKey(),
            context.getDestinationObjectKey().orElse(null),
            reconciliationId,
            null,
            delegateResponse
        );
    }

    @NonNull
    public static ObjectStorageOperationOutcome deleteSuccess(@NonNull ObjectStorageOperationContext context,
                                                              @Nullable Object delegateResponse,
                                                              @Nullable String reconciliationId) {
        Objects.requireNonNull(context, "context");
        return new ObjectStorageOperationOutcome(
            ObjectStorageOperationType.DELETE,
            context.getTenantId(),
            context.getStorageDescriptor(),
            context.getObjectKey(),
            null,
            reconciliationId,
            null,
            delegateResponse
        );
    }

    @NonNull
    public ObjectStorageOperationType getOperationType() {
        return operationType;
    }

    @NonNull
    public String getTenantId() {
        return tenantId;
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
    public Optional<String> getDestinationObjectKey() {
        return Optional.ofNullable(destinationObjectKey);
    }

    @NonNull
    public Optional<String> getReconciliationId() {
        return Optional.ofNullable(reconciliationId);
    }

    @NonNull
    public Optional<UploadResponse<?>> getUploadResponse() {
        return Optional.ofNullable(uploadResponse);
    }

    @NonNull
    public Optional<Object> getDelegateResponse() {
        return Optional.ofNullable(delegateResponse);
    }
}
