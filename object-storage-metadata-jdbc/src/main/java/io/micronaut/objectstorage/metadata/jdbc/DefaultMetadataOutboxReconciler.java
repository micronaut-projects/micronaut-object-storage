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
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState;
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageObjectMetadata;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Singleton
@Internal
final class DefaultMetadataOutboxReconciler implements MetadataOutboxReconciler {

    private final StorageObjectMetadataRepository objectRepository;
    private final OutboxStorageDescriptorResolver descriptorResolver;

    DefaultMetadataOutboxReconciler(StorageObjectMetadataRepository objectRepository,
                                    OutboxStorageDescriptorResolver descriptorResolver) {
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository");
        this.descriptorResolver = Objects.requireNonNull(descriptorResolver, "descriptorResolver");
    }

    @Override
    public void reconcile(@NonNull StorageMetadataOutboxRecord outboxRecord) {
        StorageMetadataOutboxRecord record = Objects.requireNonNull(outboxRecord, "outboxRecord");
        switch (record.getOperationType()) {
            case UPLOAD -> upsertObject(record);
            case COPY -> copyObject(record);
            case DELETE -> deleteObject(record);
            default -> throw new ObjectStorageException("Unsupported outbox operation type: " + record.getOperationType());
        }
    }

    private void upsertObject(StorageMetadataOutboxRecord record) {
        String key = record.getObjectKey().orElseThrow(() -> missingField(record, "objectKey"));
        StorageDescriptor descriptor = descriptorResolver.resolveOrFallback(record.getStorageName(), record.getLogicalContainer());
        StorageObjectMetadata metadata = newSucceededMetadata(record.getTenantId(), descriptor, key,
            record.getObjectKeyHash().orElseGet(() -> StorageObjectMetadata.deterministicObjectKeyHash(key)), null);
        objectRepository.upsert(metadata, Map.of());
    }

    private void copyObject(StorageMetadataOutboxRecord record) {
        String sourceKey = record.getObjectKey().orElseThrow(() -> missingField(record, "objectKey"));
        String sourceHash = record.getObjectKeyHash().orElseGet(() -> StorageObjectMetadata.deterministicObjectKeyHash(sourceKey));
        String destinationKey = record.getDestinationObjectKey().orElseThrow(() -> missingField(record, "destinationObjectKey"));
        String destinationHash = record.getDestinationObjectKeyHash().orElseGet(() -> StorageObjectMetadata.deterministicObjectKeyHash(destinationKey));
        StorageDescriptor descriptor = descriptorResolver.resolveOrFallback(record.getStorageName(), record.getLogicalContainer());
        StorageObjectMetadataRecord sourceRecord = objectRepository.findByTenantAndObjectHash(record.getTenantId(), descriptor, sourceHash)
            .orElseThrow(() -> new ObjectStorageException("Outbox record '" + record.getId() + "' missing source metadata for copy"));
        StorageObjectMetadata sourceMetadata = sourceRecord.metadata();
        StorageObjectMetadata destinationMetadata = newSucceededMetadata(
            record.getTenantId(),
            descriptor,
            destinationKey,
            destinationHash,
            sourceMetadata
        );
        objectRepository.upsert(destinationMetadata, sourceRecord.attributes());
    }

    private void deleteObject(StorageMetadataOutboxRecord record) {
        StorageDescriptor descriptor = descriptorResolver.resolveOrFallback(record.getStorageName(), record.getLogicalContainer());
        String hash = record.getObjectKeyHash().orElseGet(() -> record.getObjectKey()
            .map(StorageObjectMetadata::deterministicObjectKeyHash)
            .orElseThrow(() -> missingField(record, "objectKey/objectKeyHash")));
        objectRepository.deleteByTenantAndObjectHash(record.getTenantId(), descriptor, hash);
    }

    private static StorageObjectMetadata newSucceededMetadata(String tenantId,
                                                              StorageDescriptor descriptor,
                                                              String key,
                                                              String keyHash,
                                                              StorageObjectMetadata sourceMetadata) {
        Instant now = Instant.now();
        return new StorageObjectMetadata(
            tenantId,
            descriptor,
            key,
            keyHash,
            sourceMetadata == null ? null : sourceMetadata.getContentType().orElse(null),
            sourceMetadata == null || sourceMetadata.getContentLength().isEmpty() ? null : sourceMetadata.getContentLength().getAsLong(),
            sourceMetadata == null ? null : sourceMetadata.getEtag().orElse(null),
            sourceMetadata == null ? null : sourceMetadata.getProviderVersionId().orElse(null),
            sourceMetadata == null ? null : sourceMetadata.getChecksum().orElse(null),
            sourceMetadata == null ? now : sourceMetadata.getCreatedAt(),
            now,
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        );
    }

    private static ObjectStorageException missingField(StorageMetadataOutboxRecord record,
                                                       String field) {
        return new ObjectStorageException("Outbox record '" + record.getId() + "' missing " + field);
    }
}
