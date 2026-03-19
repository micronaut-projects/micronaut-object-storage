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
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext
import io.micronaut.objectstorage.metadata.ObjectStorageOperationOutcome
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import spock.lang.Specification

import java.time.Instant

class MetadataDeleteWorkflowSpec extends Specification {

    void 'delete delegates first then enqueues delete outbox record'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantId() >> 'tenant-a'
        }
        def containerMetadata = new StorageContainerMetadata('tenant-a', descriptor, Instant.parse('2026-03-17T10:15:30Z'), Instant.parse('2026-03-17T10:15:30Z'))
        def containerRecord = new StorageContainerMetadataRecord(containerMetadata, [:])
        def containerRepository = Mock(StorageContainerMetadataRepository)
        def outboxRepository = Mock(StorageMetadataOutboxRepository)
        def metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, Mock(io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository))
        def delegate = Mock(ObjectStorageOperations)
        def events = []
        def operations = new DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            outboxRepository,
            [new RecordingHook(events)]
        )

        when:
        def response = operations.delete('nested/file.txt')

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.of(containerRecord)
        1 * delegate.delete('nested/file.txt') >> {
            events << 'delegate-delete'
            'deleted-native'
        }
        1 * outboxRepository.enqueueIfAbsent(_ as StorageMetadataOutboxRecord) >> { StorageMetadataOutboxRecord record ->
            assert events == ['before:DELETE:nested/file.txt', 'delegate-delete']
            assert record.operationType.name() == 'DELETE'
            assert record.objectKey.get() == 'nested/file.txt'
            assert record.objectKeyHash.get() == StorageObjectMetadata.deterministicObjectKeyHash('nested/file.txt')
            assert !record.destinationObjectKey.present
            assert record.idempotencyKey == 'tenant-a|photos|DELETE|' + StorageObjectMetadata.deterministicObjectKeyHash('nested/file.txt') + '|-'
            events << 'enqueue-delete'
            true
        }
        response == 'deleted-native'
        events == ['before:DELETE:nested/file.txt', 'delegate-delete', 'enqueue-delete']
    }

    void 'delete duplicate enqueue replay returns delegate response without dispatch exception'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantId() >> 'tenant-a'
        }
        def containerRepository = Mock(StorageContainerMetadataRepository)
        def outboxRepository = Mock(StorageMetadataOutboxRepository)
        def metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, Mock(io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository))
        def delegate = Mock(ObjectStorageOperations)
        def events = []
        def hook = new ErrorTrackingHook(events)
        def operations = new DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            outboxRepository,
            [hook]
        )

        when:
        def response = operations.delete('nested/duplicate-delete.txt')

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.empty()
        1 * delegate.delete('nested/duplicate-delete.txt') >> 'deleted-duplicate'
        1 * outboxRepository.enqueueIfAbsent(_ as StorageMetadataOutboxRecord) >> false
        response == 'deleted-duplicate'
        notThrown(io.micronaut.objectstorage.metadata.MetadataDispatchException)
        events == ['before:DELETE:nested/duplicate-delete.txt']
        hook.errors.isEmpty()
    }

    private static final class RecordingHook implements ObjectStorageLifecycleHook {
        private final List<String> events

        RecordingHook(List<String> events) {
            this.events = events
        }

        @Override
        void before(ObjectStorageOperationContext context) {
            events << "before:${context.operationType}:${context.objectKey}".toString()
        }

        @Override
        void after(ObjectStorageOperationContext context, ObjectStorageOperationOutcome outcome) {
            events << "after:${outcome.operationType}:${outcome.objectKey}".toString()
        }
    }

    private static final class ErrorTrackingHook implements ObjectStorageLifecycleHook {
        private final List<String> events
        final List<Throwable> errors = []

        ErrorTrackingHook(List<String> events) {
            this.events = events
        }

        @Override
        void before(ObjectStorageOperationContext context) {
            events << "before:${context.operationType}:${context.objectKey}".toString()
        }

        @Override
        void error(ObjectStorageOperationContext context, Throwable throwable) {
            errors << throwable
        }
    }
}
