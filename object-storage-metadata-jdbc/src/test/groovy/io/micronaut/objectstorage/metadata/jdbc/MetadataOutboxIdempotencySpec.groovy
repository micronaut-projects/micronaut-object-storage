package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import spock.lang.Specification

import java.time.Instant

class MetadataOutboxIdempotencySpec extends Specification {

    void 'replayed idempotency key reconciles exactly once'() {
        given:
        def objectRepository = new CountingObjectMetadataRepository()
        def outboxRepository = new InMemoryOutboxRepository()
        def descriptor = new StorageDescriptor('pictures', 'oracle', 'reports', 'ns-a', 'bucket-a')
        def descriptorResolver = new OutboxStorageDescriptorResolver([({ -> descriptor } as StorageDescriptorResolver)])
        def reconciler = new DefaultMetadataOutboxReconciler(objectRepository, descriptorResolver)
        def worker = new MetadataOutboxReconciliationWorker(outboxRepository, reconciler, descriptorResolver, [], new ObjectStorageMetadataJdbcConfiguration())
        def now = Instant.parse('2026-03-17T12:00:00Z')

        and:
        def first = record('outbox-1', 'tenant-a|pictures|reports|/2026/03/invoice-001.pdf|UPLOAD', now)
        def duplicate = record('outbox-2', 'tenant-a|pictures|reports|/2026/03/invoice-001.pdf|UPLOAD', now)

        when:
        boolean inserted = outboxRepository.enqueueIfAbsent(first)
        boolean duplicateInserted = outboxRepository.enqueueIfAbsent(duplicate)
        worker.reconcileDueEntries()
        worker.reconcileDueEntries()

        then:
        inserted
        !duplicateInserted
        objectRepository.upsertCount == 1
        objectRepository.rows.size() == 1
        outboxRepository.findById('outbox-1').orElseThrow().status == StorageMetadataOutboxStatus.SUCCEEDED
    }

    private static StorageMetadataOutboxRecord record(String id, String idempotencyKey, Instant now) {
        new StorageMetadataOutboxRecord(
            id,
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
            idempotencyKey,
            now,
            now
        )
    }

    private static final class CountingObjectMetadataRepository implements StorageObjectMetadataRepository {
        int upsertCount = 0
        final Map<String, StorageObjectMetadataRecord> rows = [:]

        @Override
        void upsert(StorageObjectMetadata metadata, Map<String, String> attributes) {
            upsertCount++
            rows.put(metadata.objectKeyHash, new StorageObjectMetadataRecord(metadata, attributes))
        }

        @Override
        Optional<StorageObjectMetadataRecord> findByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
            Optional.ofNullable(rows.get(objectKeyHash))
        }

        @Override
        List<StorageObjectMetadataRecord> listByTenantAndQuery(String tenantId, StorageObjectMetadataQuery query) {
            []
        }

        @Override
        void deleteByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
            rows.remove(objectKeyHash)
        }
    }

    private static final class InMemoryOutboxRepository implements StorageMetadataOutboxRepository {
        private final Map<String, StorageMetadataOutboxRecord> rowsById = [:]
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
            idByIdempotency.put(outboxRecord.idempotencyKey, outboxRecord.id)
            rowsById.put(outboxRecord.id, outboxRecord)
            true
        }

        @Override
        List<StorageMetadataOutboxRecord> findDueEntries(Instant asOf, int limit) {
            rowsById.values().findAll {
                it.nextAttemptAt <= asOf && it.status in [StorageMetadataOutboxStatus.PENDING, StorageMetadataOutboxStatus.FAILED, StorageMetadataOutboxStatus.PROCESSING]
            }.take(limit)
        }

        @Override
        boolean markProcessing(String id, Instant asOf, Instant updatedAt) {
            def record = rowsById[id]
            if (record == null || record.nextAttemptAt > asOf || !(record.status in [StorageMetadataOutboxStatus.PENDING, StorageMetadataOutboxStatus.FAILED, StorageMetadataOutboxStatus.PROCESSING])) {
                return false
            }
            rowsById[id] = withStatus(record, StorageMetadataOutboxStatus.PROCESSING, record.attemptCount, record.nextAttemptAt, record.lastErrorSummary.orElse(null), updatedAt)
            true
        }

        @Override
        void markSucceeded(String id, Instant updatedAt) {
            def record = rowsById[id]
            rowsById[id] = withStatus(record, StorageMetadataOutboxStatus.SUCCEEDED, record.attemptCount, record.nextAttemptAt, null, updatedAt)
        }

        @Override
        void markRetryOrDeadLetter(String id, int attemptCount, Instant nextAttemptAt, StorageMetadataOutboxStatus status, String lastErrorSummary, Instant updatedAt) {
            def record = rowsById[id]
            rowsById[id] = withStatus(record, status, attemptCount, nextAttemptAt, lastErrorSummary, updatedAt)
        }

        @Override
        Optional<StorageMetadataOutboxRecord> findById(String id) {
            Optional.ofNullable(rowsById[id])
        }

        @Override
        Optional<StorageMetadataOutboxRecord> findByIdempotencyKey(String idempotencyKey) {
            Optional.ofNullable(idByIdempotency[idempotencyKey]).flatMap { findById(it) }
        }

        private static StorageMetadataOutboxRecord withStatus(StorageMetadataOutboxRecord record,
                                                              StorageMetadataOutboxStatus status,
                                                              int attempts,
                                                              Instant nextAttemptAt,
                                                              String error,
                                                              Instant updatedAt) {
            new StorageMetadataOutboxRecord(record.id, record.tenantId, record.storageName, record.logicalContainer, record.objectKey.orElse(null), record.objectKeyHash.orElse(null), record.destinationObjectKey.orElse(null), record.destinationObjectKeyHash.orElse(null), record.operationType, record.reconciliationId.orElse(null), record.payloadVersion, status, attempts, nextAttemptAt, error, record.idempotencyKey, record.createdAt, updatedAt)
        }
    }
}
