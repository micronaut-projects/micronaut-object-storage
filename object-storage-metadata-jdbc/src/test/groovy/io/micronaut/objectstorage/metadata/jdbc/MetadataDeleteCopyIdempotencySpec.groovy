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

import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import spock.lang.Specification

import java.time.Instant

class MetadataDeleteCopyIdempotencySpec extends Specification {

    void 'delete replay is safe and copy replay converges on one destination row'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def repository = new CountingObjectMetadataRepository()
        def resolver = new OutboxStorageDescriptorResolver([({ -> descriptor } as StorageDescriptorResolver)])
        def reconciler = new DefaultMetadataOutboxReconciler(repository, resolver)
        def sourceMetadata = new StorageObjectMetadata(
            'tenant-a', descriptor, 'source.txt', StorageObjectMetadata.deterministicObjectKeyHash('source.txt'),
            'text/plain', 11L, 'etag-src', 'version-src', 'sha256:src',
            Instant.parse('2026-03-17T12:00:00Z'), Instant.parse('2026-03-17T12:00:00Z'),
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        )
        def destinationMetadata = new StorageObjectMetadata(
            'tenant-a', descriptor, 'dest.txt', StorageObjectMetadata.deterministicObjectKeyHash('dest.txt'),
            'text/plain', 11L, 'etag-old', 'version-old', 'sha256:old',
            Instant.parse('2026-03-17T12:00:01Z'), Instant.parse('2026-03-17T12:00:01Z'),
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        )
        repository.upsert(sourceMetadata, [owner: 'micronaut'])
        repository.seed(destinationMetadata, [owner: 'stale'])
        def copyRecord = new StorageMetadataOutboxRecord(
            'copy-1', 'tenant-a', 'photos', 'logical-photos',
            'source.txt', StorageObjectMetadata.deterministicObjectKeyHash('source.txt'),
            'dest.txt', StorageObjectMetadata.deterministicObjectKeyHash('dest.txt'),
            io.micronaut.objectstorage.metadata.ObjectStorageOperationType.COPY,
            'recon-copy', 1, StorageMetadataOutboxStatus.PENDING, 0,
            Instant.parse('2026-03-17T12:00:02Z'), null,
            'tenant-a|photos|COPY|' + StorageObjectMetadata.deterministicObjectKeyHash('source.txt') + '|' + StorageObjectMetadata.deterministicObjectKeyHash('dest.txt'),
            Instant.parse('2026-03-17T12:00:02Z'), Instant.parse('2026-03-17T12:00:02Z')
        )
        def deleteRecord = new StorageMetadataOutboxRecord(
            'delete-1', 'tenant-a', 'photos', 'logical-photos',
            'dest.txt', StorageObjectMetadata.deterministicObjectKeyHash('dest.txt'),
            null, null,
            io.micronaut.objectstorage.metadata.ObjectStorageOperationType.DELETE,
            'recon-delete', 1, StorageMetadataOutboxStatus.PENDING, 0,
            Instant.parse('2026-03-17T12:00:03Z'), null,
            'tenant-a|photos|DELETE|' + StorageObjectMetadata.deterministicObjectKeyHash('dest.txt') + '|-',
            Instant.parse('2026-03-17T12:00:03Z'), Instant.parse('2026-03-17T12:00:03Z')
        )

        when:
        reconciler.reconcile(copyRecord)
        reconciler.reconcile(copyRecord)
        reconciler.reconcile(deleteRecord)
        reconciler.reconcile(deleteRecord)

        then:
        repository.upsertByHash[StorageObjectMetadata.deterministicObjectKeyHash('dest.txt')] == 2
        repository.deleteByHash[StorageObjectMetadata.deterministicObjectKeyHash('dest.txt')] == 2
        !repository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('dest.txt')).present
        repository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('source.txt')).orElseThrow().attributes() == [owner: 'micronaut']
    }

    private static final class CountingObjectMetadataRepository implements StorageObjectMetadataRepository {
        private final Map<String, StorageObjectMetadataRecord> rows = [:]
        final Map<String, Integer> upsertByHash = [:].withDefault { 0 }
        final Map<String, Integer> deleteByHash = [:].withDefault { 0 }

        void seed(StorageObjectMetadata metadata, Map<String, String> attributes) {
            rows[metadata.objectKeyHash] = new StorageObjectMetadataRecord(metadata, attributes)
        }

        @Override
        void upsert(StorageObjectMetadata metadata, Map<String, String> attributes) {
            upsertByHash[metadata.objectKeyHash] = upsertByHash[metadata.objectKeyHash] + 1
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
            deleteByHash[objectKeyHash] = deleteByHash[objectKeyHash] + 1
            rows.remove(objectKeyHash)
        }
    }
}
