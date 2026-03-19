package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import spock.lang.Specification

import java.time.Instant

class MetadataTenantIsolationSpec extends Specification {

    void "public query methods only use active tenant"() {
        given:
        TenantResolver tenantResolver = Stub() {
            resolveTenantId() >> 'tenant-b'
        }
        StorageContainerMetadataRepository containerRepository = Mock()
        StorageObjectMetadataRepository objectRepository = Mock()
        def descriptor = new StorageDescriptor('pictures', 'oracle', 'reports', 'ns-b', 'bucket-b')
        def object = objectMetadata('tenant-b', descriptor, '/2026/03/invoice-010.pdf')
        def query = StorageObjectMetadataQuery.all().withStorageName('pictures').withLogicalContainer('reports')
        def operations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, objectRepository)

        when:
        def container = operations.findContainer(descriptor)
        def found = operations.findObject(descriptor, '/2026/03/invoice-010.pdf')
        def listed = operations.listObjects(query)

        then:
        !container.present
        found.present
        found.get() == object
        listed == [object]

        and:
        1 * containerRepository.findByTenantAndDescriptor('tenant-b', descriptor) >> Optional.empty()
        1 * objectRepository.findByTenantAndObjectHash('tenant-b', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('/2026/03/invoice-010.pdf')) >> Optional.of(new StorageObjectMetadataRecord(object, [owner: 'bob']))
        1 * objectRepository.listByTenantAndQuery('tenant-b', query) >> [new StorageObjectMetadataRecord(object, [owner: 'bob'])]
        0 * containerRepository.findByTenantAndDescriptor('tenant-a', _)
        0 * objectRepository.findByTenantAndObjectHash('tenant-a', _, _)
        0 * objectRepository.listByTenantAndQuery('tenant-a', _)
        0 * _
    }

    private static StorageObjectMetadata objectMetadata(String tenantId, StorageDescriptor descriptor, String key) {
        Instant now = Instant.parse('2026-03-17T12:00:00Z')
        new StorageObjectMetadata(
            tenantId,
            descriptor,
            key,
            StorageObjectMetadata.deterministicObjectKeyHash(key),
            'application/pdf',
            100L,
            'etag-1',
            'version-1',
            'sha256:abc',
            now,
            now,
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        )
    }
}
