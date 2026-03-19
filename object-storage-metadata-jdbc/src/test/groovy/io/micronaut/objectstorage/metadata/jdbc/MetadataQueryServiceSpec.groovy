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

class MetadataQueryServiceSpec extends Specification {

    void "findContainer and findObject resolve active tenant and hide non terminal states"() {
        given:
        TenantResolver tenantResolver = Stub() {
            resolveTenantId() >> 'tenant-a'
        }
        StorageContainerMetadataRepository containerRepository = Mock()
        StorageObjectMetadataRepository objectRepository = Mock()
        def descriptor = new StorageDescriptor('pictures', 'oracle', 'reports', 'ns-a', 'bucket-a')
        def containerMetadata = new StorageContainerMetadata('tenant-a', descriptor, Instant.parse('2026-03-17T12:00:00Z'), Instant.parse('2026-03-17T12:00:00Z'))
        def visibleObject = objectMetadata('tenant-a', descriptor, '/2026/03/invoice-001.pdf', ObjectMetadataReconciliationState.SUCCEEDED)
        def pendingObject = objectMetadata('tenant-a', descriptor, '/2026/03/invoice-002.pdf', ObjectMetadataReconciliationState.PENDING)
        def operations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, objectRepository)

        when:
        def container = operations.findContainer(descriptor)
        def object = operations.findObject(descriptor, '/2026/03/invoice-001.pdf')
        def hiddenObject = operations.findObject(descriptor, '/2026/03/invoice-002.pdf')

        then:
        container.present
        container.get() == containerMetadata
        object.present
        object.get() == visibleObject
        !hiddenObject.present

        and:
        1 * containerRepository.findByTenantAndDescriptor('tenant-a', descriptor) >> Optional.of(new StorageContainerMetadataRecord(containerMetadata, [region: 'eu-frankfurt-1']))
        1 * objectRepository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('/2026/03/invoice-001.pdf')) >> Optional.of(new StorageObjectMetadataRecord(visibleObject, [owner: 'alice']))
        1 * objectRepository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('/2026/03/invoice-002.pdf')) >> Optional.of(new StorageObjectMetadataRecord(pendingObject, [owner: 'alice']))
        0 * _
    }

    private static StorageObjectMetadata objectMetadata(String tenantId, StorageDescriptor descriptor, String key, ObjectMetadataReconciliationState state) {
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
            new ObjectMetadataSyncStatus(state, state == ObjectMetadataReconciliationState.SUCCEEDED ? null : 'not visible yet')
        )
    }
}
