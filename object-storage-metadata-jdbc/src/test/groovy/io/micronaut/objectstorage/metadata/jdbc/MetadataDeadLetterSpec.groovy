package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class MetadataDeadLetterSpec extends Specification {

    void 'failed reconciliation retries with backoff and transitions to dead letter with error hook context'() {
        given:
        def outboxRepository = new InMemoryOutboxRepository()
        def descriptor = new StorageDescriptor('pictures', 'oracle', 'reports', 'ns-a', 'bucket-a')
        def descriptorResolver = new OutboxStorageDescriptorResolver([({ -> descriptor } as io.micronaut.objectstorage.metadata.StorageDescriptorResolver)])
        def hook = new RecordingErrorHook()
        def worker = new MetadataOutboxReconciliationWorker(
            outboxRepository,
            ({ StorageMetadataOutboxRecord ignored -> throw new IllegalStateException('boom') } as MetadataOutboxReconciler),
            descriptorResolver,
            [hook],
            new ObjectStorageMetadataJdbcConfiguration()
        )
        def now = Instant.parse('2026-03-17T12:00:00Z')

        and:
        outboxRepository.enqueue(new StorageMetadataOutboxRecord(
            'outbox-1',
            'tenant-a',
            'pictures',
            'reports',
            '/2026/03/invoice-001.pdf',
            StorageObjectMetadata.deterministicObjectKeyHash('/2026/03/invoice-001.pdf'),
            null,
            null,
            io.micronaut.objectstorage.metadata.ObjectStorageOperationType.UPLOAD,
            'recon-1',
            1,
            StorageMetadataOutboxStatus.PENDING,
            0,
            now.minusSeconds(1),
            null,
            'tenant-a|pictures|reports|/2026/03/invoice-001.pdf|UPLOAD',
            now,
            now
        ))

        when:
        5.times {
            worker.reconcileDueEntries()
            outboxRepository.forceDue('outbox-1')
        }
        def outbox = outboxRepository.findById('outbox-1').orElseThrow()

        then:
        outbox.status == StorageMetadataOutboxStatus.DEAD_LETTER
        outbox.attemptCount == 5
        outbox.lastErrorSummary.orElse('').contains('boom')
        hook.contexts.size() == 5
        hook.contexts.last().tenantId == 'tenant-a'
        hook.contexts.last().objectKey == '/2026/03/invoice-001.pdf'

        and:
        MetadataOutboxRetryPolicy.delayForAttempt(1) == Duration.ofMillis(250)
        MetadataOutboxRetryPolicy.delayForAttempt(2) == Duration.ofSeconds(1)
        MetadataOutboxRetryPolicy.delayForAttempt(3) == Duration.ofSeconds(5)
        MetadataOutboxRetryPolicy.delayForAttempt(4) == Duration.ofSeconds(15)
        MetadataOutboxRetryPolicy.delayForAttempt(5) == Duration.ofSeconds(30)
        MetadataOutboxRetryPolicy.delayForAttempt(10) == Duration.ofSeconds(30)
    }

    private static final class RecordingErrorHook implements ObjectStorageLifecycleHook {
        final List<ObjectStorageOperationContext> contexts = []

        @Override
        void error(ObjectStorageOperationContext context, Throwable throwable) {
            contexts << context
        }
    }

    private static final class InMemoryOutboxRepository implements StorageMetadataOutboxRepository {
        private final Map<String, StorageMetadataOutboxRecord> rows = [:]
        private final Map<String, String> idByIdempotency = [:]

        @Override
        void enqueue(StorageMetadataOutboxRecord outboxRecord) {
            enqueueIfAbsent(outboxRecord)
        }

        @Override
        boolean enqueueIfAbsent(StorageMetadataOutboxRecord outboxRecord) {
            if (idByIdempotency.containsKey(outboxRecord.idempotencyKey)) {
                return false
            }
            idByIdempotency[outboxRecord.idempotencyKey] = outboxRecord.id
            rows[outboxRecord.id] = outboxRecord
            true
        }

        @Override
        List<StorageMetadataOutboxRecord> findDueEntries(Instant asOf, int limit) {
            rows.values().findAll {
                it.nextAttemptAt <= asOf && it.status in [StorageMetadataOutboxStatus.PENDING, StorageMetadataOutboxStatus.FAILED, StorageMetadataOutboxStatus.PROCESSING]
            }.take(limit)
        }

        @Override
        boolean markProcessing(String id, Instant asOf, Instant updatedAt) {
            def record = rows[id]
            if (record == null || record.nextAttemptAt > asOf || !(record.status in [StorageMetadataOutboxStatus.PENDING, StorageMetadataOutboxStatus.FAILED, StorageMetadataOutboxStatus.PROCESSING])) {
                return false
            }
            rows[id] = withStatus(record, StorageMetadataOutboxStatus.PROCESSING, record.attemptCount, record.nextAttemptAt, record.lastErrorSummary.orElse(null), updatedAt)
            true
        }

        @Override
        void markSucceeded(String id, Instant updatedAt) {
            def record = rows[id]
            rows[id] = withStatus(record, StorageMetadataOutboxStatus.SUCCEEDED, record.attemptCount, record.nextAttemptAt, null, updatedAt)
        }

        @Override
        void markRetryOrDeadLetter(String id, int attemptCount, Instant nextAttemptAt, StorageMetadataOutboxStatus status, String lastErrorSummary, Instant updatedAt) {
            def record = rows[id]
            rows[id] = withStatus(record, status, attemptCount, nextAttemptAt, lastErrorSummary, updatedAt)
        }

        @Override
        Optional<StorageMetadataOutboxRecord> findById(String id) {
            Optional.ofNullable(rows[id])
        }

        @Override
        Optional<StorageMetadataOutboxRecord> findByIdempotencyKey(String idempotencyKey) {
            Optional.ofNullable(idByIdempotency[idempotencyKey]).flatMap { findById(it) }
        }

        void forceDue(String id) {
            def record = rows[id]
            rows[id] = withStatus(record, record.status, record.attemptCount, Instant.now().minusMillis(1), record.lastErrorSummary.orElse(null), record.updatedAt)
        }

        private static StorageMetadataOutboxRecord withStatus(StorageMetadataOutboxRecord record,
                                                              StorageMetadataOutboxStatus status,
                                                              int attemptCount,
                                                              Instant nextAttemptAt,
                                                              String lastErrorSummary,
                                                              Instant updatedAt) {
            new StorageMetadataOutboxRecord(record.id, record.tenantId, record.storageName, record.logicalContainer, record.objectKey.orElse(null), record.objectKeyHash.orElse(null), record.destinationObjectKey.orElse(null), record.destinationObjectKeyHash.orElse(null), record.operationType, record.reconciliationId.orElse(null), record.payloadVersion, status, attemptCount, nextAttemptAt, lastErrorSummary, record.idempotencyKey, record.createdAt, updatedAt)
        }
    }
}
