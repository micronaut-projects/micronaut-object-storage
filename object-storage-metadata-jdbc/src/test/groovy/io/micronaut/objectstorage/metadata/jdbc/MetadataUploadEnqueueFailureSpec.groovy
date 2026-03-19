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
import io.micronaut.objectstorage.metadata.MetadataDispatchException
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

class MetadataUploadEnqueueFailureSpec extends Specification {

    void 'upload throws metadata dispatch exception when enqueue fails after storage success'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantId() >> 'tenant-a'
        }
        def containerRepository = Mock(StorageContainerMetadataRepository)
        def outboxRepository = Mock(StorageMetadataOutboxRepository)
        def metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, Mock(io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository))
        def delegate = Mock(ObjectStorageOperations)
        def operations = new DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            outboxRepository,
            []
        )
        def request = UploadRequest.fromBytes('body'.bytes, 'nested/file.txt', 'text/plain')
        def uploadResponse = UploadResponse.of(request.key, 'etag-1', 'native-response')

        when:
        operations.upload(request)

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.empty()
        1 * delegate.upload(request) >> uploadResponse
        1 * outboxRepository.enqueueIfAbsent(_ as StorageMetadataOutboxRecord) >> { throw new IllegalStateException('db down') }
        def ex = thrown(MetadataDispatchException)
        ex.operationContext.tenantId == 'tenant-a'
        ex.operationContext.storageDescriptor == descriptor
        ex.operationContext.objectKey == 'nested/file.txt'
        ex.operationOutcome.uploadResponse.get().is(uploadResponse)
        ex.reconciliationId.present
        ex.cause.message == 'db down'
    }

    void 'partial-completion exception retains reconciliation and upload outcome details'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantId() >> 'tenant-z'
        }
        def containerRepository = Mock(StorageContainerMetadataRepository)
        def outboxRepository = Mock(StorageMetadataOutboxRepository)
        def metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, Mock(io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository))
        def delegate = Mock(ObjectStorageOperations)
        def operations = new DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            outboxRepository,
            []
        )
        def request = UploadRequest.fromBytes('body'.bytes, 'nested/partial.txt', 'text/plain')
        def uploadResponse = UploadResponse.of(request.key, 'etag-2', 'native-response-2')

        when:
        operations.upload(request)

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-z', descriptor) >> Optional.empty()
        1 * delegate.upload(request) >> uploadResponse
        1 * outboxRepository.enqueueIfAbsent(_ as StorageMetadataOutboxRecord) >> { throw new IllegalStateException('dispatch write failed') }
        def ex = thrown(MetadataDispatchException)
        ex.operationContext.tenantId == 'tenant-z'
        ex.operationContext.objectKey == 'nested/partial.txt'
        ex.operationOutcome.uploadResponse.present
        ex.operationOutcome.uploadResponse.get().is(uploadResponse)
        ex.reconciliationId.present
        ex.cause.message == 'dispatch write failed'
    }
}
