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
package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import spock.lang.Specification

import java.time.Instant

class MetadataCopyWorkflowSpec extends Specification {

    void 'copy delegates first then reconciliation creates destination metadata from source metadata'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def objectRepository = new InMemoryObjectMetadataRepository()
        def descriptorResolver = new OutboxStorageDescriptorResolver([({ -> descriptor } as StorageDescriptorResolver)])
        def reconciler = new DefaultMetadataOutboxReconciler(objectRepository, descriptorResolver)
        def sourceMetadata = new StorageObjectMetadata(
            'tenant-a',
            descriptor,
            'source.txt',
            StorageObjectMetadata.deterministicObjectKeyHash('source.txt'),
            'text/plain',
            42L,
            'etag-1',
            'version-1',
            'sha256:abc',
            Instant.parse('2026-03-17T12:00:00Z'),
            Instant.parse('2026-03-17T12:00:00Z'),
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        )
        objectRepository.upsert(sourceMetadata, [owner: 'micronaut', env: 'test'])
        def outboxRecord = new StorageMetadataOutboxRecord(
            'copy-1',
            'tenant-a',
            'photos',
            'logical-photos',
            'source.txt',
            StorageObjectMetadata.deterministicObjectKeyHash('source.txt'),
            'dest.txt',
            StorageObjectMetadata.deterministicObjectKeyHash('dest.txt'),
            io.micronaut.objectstorage.metadata.ObjectStorageOperationType.COPY,
            'recon-copy-1',
            1,
            StorageMetadataOutboxStatus.PENDING,
            0,
            Instant.parse('2026-03-17T12:00:01Z'),
            null,
            'tenant-a|photos|COPY|' + StorageObjectMetadata.deterministicObjectKeyHash('source.txt') + '|' + StorageObjectMetadata.deterministicObjectKeyHash('dest.txt'),
            Instant.parse('2026-03-17T12:00:01Z'),
            Instant.parse('2026-03-17T12:00:01Z')
        )

        when:
        reconciler.reconcile(outboxRecord)

        then:
        def copied = objectRepository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('dest.txt')).orElseThrow()
        copied.metadata().objectKey == 'dest.txt'
        copied.metadata().objectKeyHash == StorageObjectMetadata.deterministicObjectKeyHash('dest.txt')
        copied.metadata().contentType.get() == 'text/plain'
        copied.metadata().contentLength.getAsLong() == 42L
        copied.metadata().etag.get() == 'etag-1'
        copied.metadata().providerVersionId.get() == 'version-1'
        copied.metadata().checksum.get() == 'sha256:abc'
        copied.attributes() == [env: 'test', owner: 'micronaut']
        objectRepository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('source.txt')).orElseThrow().metadata().objectKey == 'source.txt'
    }

    private static final class InMemoryObjectMetadataRepository implements StorageObjectMetadataRepository {
        private final Map<String, StorageObjectMetadataRecord> rows = [:]

        @Override
        void upsert(StorageObjectMetadata metadata, Map<String, String> attributes) {
            rows[metadata.objectKeyHash] = new StorageObjectMetadataRecord(metadata, attributes)
        }

        @Override
        Optional<StorageObjectMetadataRecord> findByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
            Optional.ofNullable(rows[objectKeyHash])
        }

        @Override
        List<StorageObjectMetadataRecord> listByTenantAndQuery(String tenantId, StorageObjectMetadataQuery query) {
            []
        }

        @Override
        void deleteByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
            rows.remove(objectKeyHash)
        }
    }
}
