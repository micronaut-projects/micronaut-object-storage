package io.micronaut.objectstorage.metadata.jdbc.oracle

import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.request.UploadRequest

class MetadataJdbcOracleIntegrationSpec extends AbstractOracleMetadataJdbcSpec {

    void 'oracle integration verifies upload query copy delete reconciliation and tenant isolation'() {
        given:
        def upload = UploadRequest.fromBytes('hello oracle'.bytes, 'reports/2026/summary.txt', 'text/plain')
        upload.metadata = [owner: 'tenant-a', source: 'oracle-spec']

        when:
        operations.upload(upload)
        reconcileAll()

        then:
        metadataOperations.findObject(descriptor, 'reports/2026/summary.txt').present
        metadataOperations.listObjects(StorageObjectMetadataQuery.all().withStorageName('default').withLogicalContainer('default'))*.objectKey == ['reports/2026/summary.txt']

        when:
        operations.copy('reports/2026/summary.txt', 'reports/2026/summary-copy.txt')
        reconcileAll()

        then:
        metadataOperations.findObject(descriptor, 'reports/2026/summary-copy.txt').present

        when:
        operations.delete('reports/2026/summary.txt')
        reconcileAll()

        then:
        !metadataOperations.findObject(descriptor, 'reports/2026/summary.txt').present
        metadataOperations.findObject(descriptor, 'reports/2026/summary-copy.txt').present

        when:
        tenantResolver.tenantId = 'tenant-b'

        then:
        !metadataOperations.findObject(descriptor, 'reports/2026/summary-copy.txt').present
        metadataOperations.listObjects(StorageObjectMetadataQuery.all().withStorageName('default').withLogicalContainer('default')).isEmpty()
    }
}
