package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import spock.lang.Specification

import java.time.Instant

class MetadataPrefixQuerySpec extends Specification {

    void "listObjects applies storage container prefix filtering through repository and hides invisible rows"() {
        given:
        TenantResolver tenantResolver = Stub() {
            resolveTenantId() >> 'tenant-a'
        }
        StorageContainerMetadataRepository containerRepository = Stub()
        StorageObjectMetadataRepository objectRepository = Mock()
        def descriptor = new StorageDescriptor('pictures', 'oracle', 'reports', 'ns-a', 'bucket-a')
        def query = StorageObjectMetadataQuery.all()
            .withStorageName('pictures')
            .withLogicalContainer('reports')
            .withObjectKeyPrefix('/2026/03/')
        def matchingOne = objectMetadata(descriptor, '/2026/03/invoice-001.pdf', ObjectMetadataReconciliationState.SUCCEEDED)
        def matchingTwo = objectMetadata(descriptor, '/2026/03/invoice-002.pdf', ObjectMetadataReconciliationState.SUCCEEDED)
        def hidden = objectMetadata(descriptor, '/2026/03/invoice-003.pdf', ObjectMetadataReconciliationState.PROCESSING)
        def operations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, objectRepository)

        when:
        def results = operations.listObjects(query)

        then:
        results == [matchingOne, matchingTwo]

        and:
        1 * objectRepository.listByTenantAndQuery('tenant-a', query) >> [
            new StorageObjectMetadataRecord(matchingOne, [owner: 'alice']),
            new StorageObjectMetadataRecord(matchingTwo, [owner: 'bob']),
            new StorageObjectMetadataRecord(hidden, [owner: 'carol'])
        ]
        0 * _
    }

    private static StorageObjectMetadata objectMetadata(StorageDescriptor descriptor, String key, ObjectMetadataReconciliationState state) {
        Instant now = Instant.parse('2026-03-17T12:00:00Z')
        new StorageObjectMetadata(
            'tenant-a',
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
            new ObjectMetadataSyncStatus(state, state == ObjectMetadataReconciliationState.SUCCEEDED ? null : 'still reconciling')
        )
    }
}
