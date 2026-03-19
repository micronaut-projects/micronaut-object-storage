package io.micronaut.objectstorage.metadata.jdbc.oracle

import io.micronaut.objectstorage.metadata.MetadataDispatchException
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.request.UploadRequest

class MetadataJdbcOracleFailureSpec extends AbstractOracleMetadataJdbcSpec {

    void 'oracle failure path reports enqueue failure and preserves tenant isolation'() {
        given:
        def failingOutboxRepository = [
            enqueue: { throw new IllegalStateException('oracle enqueue failed') },
            enqueueIfAbsent: { throw new IllegalStateException('oracle enqueue failed') },
            findDueEntries: { _, _ -> [] },
            markProcessing: { _, _, _ -> false },
            markSucceeded: { _, _ -> },
            markRetryOrDeadLetter: { _, _, _, _, _, _ -> },
            findById: { Optional.empty() },
            findByIdempotencyKey: { Optional.empty() }
        ] as StorageMetadataOutboxRepository
        def failingOperations = new io.micronaut.objectstorage.metadata.jdbc.DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            failingOutboxRepository,
            []
        )
        def upload = UploadRequest.fromBytes('hello oracle'.bytes, 'reports/2026/failure.txt', 'text/plain')

        when:
        failingOperations.upload(upload)

        then:
        def ex = thrown(MetadataDispatchException)
        ex.operationContext.tenantId == 'tenant-a'
        ex.operationContext.objectKey == 'reports/2026/failure.txt'
        ex.cause.message == 'oracle enqueue failed'
        !metadataOperations.findObject(descriptor, 'reports/2026/failure.txt').present

        when:
        tenantResolver.tenantId = 'tenant-b'

        then:
        !metadataOperations.findObject(descriptor, 'reports/2026/failure.txt').present
    }
}
