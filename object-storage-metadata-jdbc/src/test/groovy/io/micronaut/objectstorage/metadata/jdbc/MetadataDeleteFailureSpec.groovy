package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import spock.lang.Specification

class MetadataDeleteFailureSpec extends Specification {

    void 'failed delete does not enqueue tombstone intent or expose false metadata state'() {
        given:
        def descriptor = new StorageDescriptor('photos', 'oracle-cloud', 'logical-photos', 'ns1', 'bucket-a')
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantId() >> 'tenant-a'
        }
        def containerRepository = Mock(StorageContainerMetadataRepository)
        def outboxRepository = Mock(StorageMetadataOutboxRepository)
        def objectRepository = Mock(io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository) {
            findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('nested/file.txt')) >> Optional.empty()
        }
        def metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, objectRepository)
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

        when:
        operations.delete('nested/file.txt')

        then:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.empty()
        1 * delegate.delete('nested/file.txt') >> { throw new ObjectStorageException('delete failed') }
        0 * outboxRepository._
        def ex = thrown(ObjectStorageException)
        ex.message == 'delete failed'
        !metadataOperations.findObject(descriptor, 'nested/file.txt').present
    }
}
