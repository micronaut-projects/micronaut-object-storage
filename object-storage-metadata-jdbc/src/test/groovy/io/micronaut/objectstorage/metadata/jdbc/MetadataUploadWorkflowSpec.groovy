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
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.time.Instant

class MetadataUploadWorkflowSpec extends Specification {

    void 'upload resolves tenant and container, fires ordered before hooks, delegates upload, and enqueues outbox'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantId() >> 'tenant-a'
        }
        def containerMetadata = new StorageContainerMetadata('tenant-a', descriptor, Instant.parse('2026-03-17T10:15:30Z'), Instant.parse('2026-03-17T10:15:30Z'))
        def containerRecord = new StorageContainerMetadataRecord(containerMetadata, [region: 'iad'])
        def containerRepository = Mock(StorageContainerMetadataRepository)
        def outboxRepository = Mock(StorageMetadataOutboxRepository)
        def metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, Mock(io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository))
        def delegate = Mock(ObjectStorageOperations)
        def events = []
        def slowerHook = new RecordingHook(10, 'slower', events)
        def fasterHook = new RecordingHook(0, 'faster', events)
        def operations = new DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            outboxRepository,
            [slowerHook, fasterHook]
        )
        def request = UploadRequest.fromBytes('body'.bytes, 'nested/file.txt', 'text/plain')
        request.metadata = [owner: 'micronaut']
        def uploadResponse = UploadResponse.of(request.key, 'etag-1', 'native-response')

        when:
        def response = operations.upload(request)

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.of(containerRecord)
        1 * delegate.upload(request) >> uploadResponse
        1 * outboxRepository.enqueueIfAbsent(_ as StorageMetadataOutboxRecord) >> { StorageMetadataOutboxRecord record ->
            assert record.tenantId == 'tenant-a'
            assert record.storageName == 'photos'
            assert record.logicalContainer == 'logical-photos'
            assert record.objectKey.get() == 'nested/file.txt'
            assert record.objectKeyHash.get() == StorageObjectMetadata.deterministicObjectKeyHash('nested/file.txt')
            assert record.reconciliationId.present
            assert record.idempotencyKey.contains('tenant-a|photos|UPLOAD|' + StorageObjectMetadata.deterministicObjectKeyHash('nested/file.txt'))
        }
        response.is(uploadResponse)
        events == ['before:faster:tenant-a:nested/file.txt', 'before:slower:tenant-a:nested/file.txt']
        fasterHook.seenContexts[0].storageDescriptor == descriptor
        slowerHook.seenContexts[0].storageDescriptor == descriptor
        fasterHook.seenContexts[0].uploadRequest.get().is(request)
    }

    void 'upload duplicate enqueue replay returns delegate response without dispatch exception'() {
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
        def request = UploadRequest.fromBytes('body'.bytes, 'nested/duplicate.txt', 'text/plain')
        def uploadResponse = UploadResponse.of(request.key, 'etag-duplicate', 'native-response-duplicate')

        when:
        def response = operations.upload(request)

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.empty()
        1 * delegate.upload(request) >> uploadResponse
        1 * outboxRepository.enqueueIfAbsent(_ as StorageMetadataOutboxRecord) >> false
        response.is(uploadResponse)
        notThrown(io.micronaut.objectstorage.metadata.MetadataDispatchException)
        events == ['before:UPLOAD:nested/duplicate.txt']
        hook.errors.isEmpty()
    }

    private static final class RecordingHook implements ObjectStorageLifecycleHook {
        final int order
        final String name
        final List<String> events
        final List<ObjectStorageOperationContext> seenContexts = []

        RecordingHook(int order, String name, List<String> events) {
            this.order = order
            this.name = name
            this.events = events
        }

        @Override
        int getOrder() {
            return order
        }

        @Override
        void before(ObjectStorageOperationContext context) {
            seenContexts << context
            events << "before:${name}:${context.tenantId}:${context.objectKey}".toString()
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
