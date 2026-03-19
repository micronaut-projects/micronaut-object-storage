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

import io.micronaut.objectstorage.request.UploadRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Context for lifecycle hook callbacks around storage operations.
 *
 * @since 1.6.0
 */
public final class ObjectStorageOperationContext {

    private final String tenantId;
    private final StorageDescriptor storageDescriptor;
    private final ObjectStorageOperationType operationType;
    private final String objectKey;
    @Nullable
    private final String destinationObjectKey;
    @Nullable
    private final UploadRequest uploadRequest;

    public ObjectStorageOperationContext(@NonNull String tenantId,
                                         @NonNull StorageDescriptor storageDescriptor,
                                         @NonNull ObjectStorageOperationType operationType,
                                         @NonNull String objectKey,
                                         @Nullable String destinationObjectKey,
                                         @Nullable UploadRequest uploadRequest) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.storageDescriptor = Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        this.operationType = Objects.requireNonNull(operationType, "operationType");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.destinationObjectKey = destinationObjectKey;
        this.uploadRequest = uploadRequest;
    }

    @NonNull
    public static ObjectStorageOperationContext forUpload(@NonNull String tenantId,
                                                          @NonNull StorageDescriptor storageDescriptor,
                                                          @NonNull UploadRequest uploadRequest) {
        Objects.requireNonNull(uploadRequest, "uploadRequest");
        return new ObjectStorageOperationContext(
            tenantId,
            storageDescriptor,
            ObjectStorageOperationType.UPLOAD,
            uploadRequest.getKey(),
            null,
            uploadRequest
        );
    }

    @NonNull
    public static ObjectStorageOperationContext forCopy(@NonNull String tenantId,
                                                        @NonNull StorageDescriptor storageDescriptor,
                                                        @NonNull String sourceKey,
                                                        @NonNull String destinationKey) {
        return new ObjectStorageOperationContext(
            tenantId,
            storageDescriptor,
            ObjectStorageOperationType.COPY,
            sourceKey,
            Objects.requireNonNull(destinationKey, "destinationKey"),
            null
        );
    }

    @NonNull
    public static ObjectStorageOperationContext forDelete(@NonNull String tenantId,
                                                          @NonNull StorageDescriptor storageDescriptor,
                                                          @NonNull String objectKey) {
        return new ObjectStorageOperationContext(
            tenantId,
            storageDescriptor,
            ObjectStorageOperationType.DELETE,
            objectKey,
            null,
            null
        );
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
    public ObjectStorageOperationType getOperationType() {
        return operationType;
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
    public Optional<UploadRequest> getUploadRequest() {
        return Optional.ofNullable(uploadRequest);
    }
}
