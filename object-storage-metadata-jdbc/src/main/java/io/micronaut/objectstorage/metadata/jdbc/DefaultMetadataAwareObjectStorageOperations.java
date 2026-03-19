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
package io.micronaut.objectstorage.metadata.jdbc;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations;
import io.micronaut.objectstorage.metadata.MetadataDispatchException;
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook;
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext;
import io.micronaut.objectstorage.metadata.ObjectStorageOperationOutcome;
import io.micronaut.objectstorage.metadata.ObjectStorageOperationType;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageObjectMetadata;
import io.micronaut.objectstorage.metadata.TenantResolver;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Internal
final class DefaultMetadataAwareObjectStorageOperations<I, O, D> implements MetadataAwareObjectStorageOperations<I, O, D> {

    private static final int OUTBOX_PAYLOAD_VERSION = 1;

    private final ObjectStorageOperations<I, O, D> delegate;
    private final ObjectStorageMetadataOperations metadataOperations;
    private final StorageDescriptor storageDescriptor;
    private final TenantResolver tenantResolver;
    private final StorageContainerMetadataRepository containerRepository;
    private final StorageMetadataOutboxRepository outboxRepository;
    private final Collection<ObjectStorageLifecycleHook> lifecycleHooks;

    DefaultMetadataAwareObjectStorageOperations(ObjectStorageOperations<I, O, D> delegate,
                                                ObjectStorageMetadataOperations metadataOperations,
                                                StorageDescriptor storageDescriptor,
                                                TenantResolver tenantResolver,
                                                StorageContainerMetadataRepository containerRepository,
                                                StorageMetadataOutboxRepository outboxRepository,
                                                Collection<ObjectStorageLifecycleHook> lifecycleHooks) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metadataOperations = Objects.requireNonNull(metadataOperations, "metadataOperations");
        this.storageDescriptor = Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver");
        this.containerRepository = Objects.requireNonNull(containerRepository, "containerRepository");
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.lifecycleHooks = List.copyOf(Objects.requireNonNull(lifecycleHooks, "lifecycleHooks"));
    }

    @Override
    public @NonNull ObjectStorageMetadataOperations getMetadataOperations() {
        return metadataOperations;
    }

    @Override
    public @NonNull Collection<ObjectStorageLifecycleHook> getLifecycleHooks() {
        return lifecycleHooks;
    }

    @Override
    public @NonNull UploadResponse<O> upload(@NonNull UploadRequest uploadRequest) {
        UploadRequest request = Objects.requireNonNull(uploadRequest, "uploadRequest");
        String tenantId = tenantResolver.resolveTenantId();
        ObjectStorageOperationContext context = ObjectStorageOperationContext.forUpload(tenantId, storageDescriptor, request);
        StorageContainerMetadataRecord containerRecord = containerRepository.findByTenantAndDescriptor(tenantId, storageDescriptor).orElse(null);
        fireBeforeHooks(context);
        UploadResponse<O> response = delegate.upload(request);
        StorageMetadataOutboxRecord outboxRecord = newOutboxRecord(tenantId, ObjectStorageOperationType.UPLOAD, request.getKey(), null, containerRecord);
        return enqueueAfterDelegateSuccess(context, outboxRecord, response);
    }

    @Override
    public @NonNull UploadResponse<O> upload(@NonNull UploadRequest uploadRequest, @NonNull Consumer<I> requestConsumer) {
        UploadRequest request = Objects.requireNonNull(uploadRequest, "uploadRequest");
        Objects.requireNonNull(requestConsumer, "requestConsumer");
        String tenantId = tenantResolver.resolveTenantId();
        ObjectStorageOperationContext context = ObjectStorageOperationContext.forUpload(tenantId, storageDescriptor, request);
        StorageContainerMetadataRecord containerRecord = containerRepository.findByTenantAndDescriptor(tenantId, storageDescriptor).orElse(null);
        fireBeforeHooks(context);
        UploadResponse<O> response = delegate.upload(request, requestConsumer);
        StorageMetadataOutboxRecord outboxRecord = newOutboxRecord(tenantId, ObjectStorageOperationType.UPLOAD, request.getKey(), null, containerRecord);
        return enqueueAfterDelegateSuccess(context, outboxRecord, response);
    }

    @Override
    public @NonNull <E extends ObjectStorageEntry<?>> Optional<E> retrieve(@NonNull String key) {
        return delegate.retrieve(key);
    }

    @Override
    public @NonNull D delete(@NonNull String key) {
        String objectKey = Objects.requireNonNull(key, "key");
        String tenantId = tenantResolver.resolveTenantId();
        ObjectStorageOperationContext context = ObjectStorageOperationContext.forDelete(tenantId, storageDescriptor, objectKey);
        StorageContainerMetadataRecord containerRecord = containerRepository.findByTenantAndDescriptor(tenantId, storageDescriptor).orElse(null);
        fireBeforeHooks(context);
        D response = delegate.delete(objectKey);
        StorageMetadataOutboxRecord outboxRecord = newOutboxRecord(tenantId, ObjectStorageOperationType.DELETE, objectKey, null, containerRecord);
        return enqueueAfterDelegateSuccess(context, outboxRecord, response);
    }

    @Override
    public boolean exists(@NonNull String key) {
        return delegate.exists(key);
    }

    @Override
    public @NonNull Set<String> listObjects() {
        return delegate.listObjects();
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        String resolvedSourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        String resolvedDestinationKey = Objects.requireNonNull(destinationKey, "destinationKey");
        String tenantId = tenantResolver.resolveTenantId();
        ObjectStorageOperationContext context = ObjectStorageOperationContext.forCopy(tenantId, storageDescriptor, resolvedSourceKey, resolvedDestinationKey);
        StorageContainerMetadataRecord containerRecord = containerRepository.findByTenantAndDescriptor(tenantId, storageDescriptor).orElse(null);
        fireBeforeHooks(context);
        delegate.copy(resolvedSourceKey, resolvedDestinationKey);
        StorageMetadataOutboxRecord outboxRecord = newOutboxRecord(tenantId, ObjectStorageOperationType.COPY, resolvedSourceKey, resolvedDestinationKey, containerRecord);
        enqueueAfterDelegateSuccess(context, outboxRecord, null);
    }

    private void fireBeforeHooks(ObjectStorageOperationContext context) {
        for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
            hook.before(context);
        }
    }

    private void fireErrorHooks(ObjectStorageOperationContext context,
                                Throwable throwable) {
        for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
            hook.error(context, throwable);
        }
    }

    private <T> T enqueueAfterDelegateSuccess(ObjectStorageOperationContext context,
                                              StorageMetadataOutboxRecord outboxRecord,
                                              @Nullable T response) {
        try {
            boolean enqueued = outboxRepository.enqueueIfAbsent(outboxRecord);
            if (!enqueued) {
                return response;
            }
            return response;
        } catch (RuntimeException e) {
            String reconciliationId = outboxRecord.getReconciliationId().orElse(null);
            ObjectStorageOperationOutcome outcome = toOperationOutcome(context, response, reconciliationId);
            fireErrorHooks(context, e);
            throw new MetadataDispatchException(context, outcome, reconciliationId, e);
        }
    }

    private static ObjectStorageOperationOutcome toOperationOutcome(ObjectStorageOperationContext context,
                                                                    @Nullable Object response,
                                                                    @Nullable String reconciliationId) {
        return switch (context.getOperationType()) {
            case UPLOAD -> {
                if (!(response instanceof UploadResponse<?> uploadResponse)) {
                    throw new IllegalStateException("Upload operation requires UploadResponse outcome");
                }
                yield ObjectStorageOperationOutcome.uploadSuccess(context, uploadResponse, reconciliationId);
            }
            case DELETE -> ObjectStorageOperationOutcome.deleteSuccess(context, response, reconciliationId);
            case COPY -> ObjectStorageOperationOutcome.copySuccess(context, response, reconciliationId);
        };
    }

    private StorageMetadataOutboxRecord newOutboxRecord(String tenantId,
                                                        ObjectStorageOperationType operationType,
                                                        String objectKey,
                                                        @Nullable String destinationObjectKey,
                                                        @Nullable StorageContainerMetadataRecord containerRecord) {
        Instant now = Instant.now();
        String reconciliationId = UUID.randomUUID().toString();
        String objectKeyHash = StorageObjectMetadata.deterministicObjectKeyHash(objectKey);
        String logicalContainer = containerRecord == null
            ? storageDescriptor.getLogicalContainer()
            : containerRecord.metadata().getStorageDescriptor().getLogicalContainer();
        String destinationKeyHash = destinationObjectKey == null ? null : StorageObjectMetadata.deterministicObjectKeyHash(destinationObjectKey);
        String idempotencyKey = tenantId + "|" + storageDescriptor.getStorageName() + "|"
            + operationType.name() + "|" + objectKeyHash + "|" + Objects.toString(destinationKeyHash, "-");
        return StorageMetadataOutboxRecord.builder()
            .id(UUID.randomUUID().toString())
            .tenantId(tenantId)
            .storageName(storageDescriptor.getStorageName())
            .logicalContainer(logicalContainer)
            .objectKey(objectKey)
            .objectKeyHash(objectKeyHash)
            .destinationObjectKey(destinationObjectKey)
            .destinationObjectKeyHash(destinationKeyHash)
            .operationType(operationType)
            .reconciliationId(reconciliationId)
            .payloadVersion(OUTBOX_PAYLOAD_VERSION)
            .status(StorageMetadataOutboxStatus.PENDING)
            .attemptCount(0)
            .nextAttemptAt(now)
            .lastErrorSummary(null)
            .idempotencyKey(idempotencyKey)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
