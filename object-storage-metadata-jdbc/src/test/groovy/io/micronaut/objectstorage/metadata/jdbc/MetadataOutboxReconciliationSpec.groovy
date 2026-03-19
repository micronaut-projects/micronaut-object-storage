package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext
import io.micronaut.objectstorage.metadata.ObjectStorageOperationOutcome
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

class MetadataOutboxReconciliationSpec extends Specification {

    void 'pending outbox entry is reconciled and marks metadata row as succeeded'() {
        given:
        def objectRepository = new InMemoryObjectMetadataRepository()
        def outboxRepository = new InMemoryOutboxRepository()
        def descriptor = new StorageDescriptor('pictures', 'oracle', 'reports', 'ns-a', 'bucket-a')
        def descriptorResolver = new OutboxStorageDescriptorResolver([new FixedDescriptorResolver(descriptor)])
        def reconciler = new DefaultMetadataOutboxReconciler(objectRepository, descriptorResolver)
        def configuration = new ObjectStorageMetadataJdbcConfiguration()
        def events = []
        def worker = new MetadataOutboxReconciliationWorker(outboxRepository, reconciler, descriptorResolver, [new RecordingHook(events)], configuration)
        def now = Instant.parse('2026-03-17T12:00:00Z')
        def record = new StorageMetadataOutboxRecord(
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
        )

        when:
        outboxRepository.enqueue(record)
        worker.reconcileDueEntries()
        def outbox = outboxRepository.findById('outbox-1').orElseThrow()
        def metadata = objectRepository.findByTenantAndObjectHash('tenant-a', descriptor, StorageObjectMetadata.deterministicObjectKeyHash('/2026/03/invoice-001.pdf')).orElseThrow().metadata()
        assert outbox.lastErrorSummary.orElse(null) == null

        then:
        outbox.status == StorageMetadataOutboxStatus.SUCCEEDED
        metadata.syncStatus.reconciliationState == ObjectMetadataReconciliationState.SUCCEEDED
        assert events == ['after:tenant-a:UPLOAD:/2026/03/invoice-001.pdf:recon-1']
    }

    private static final class RecordingHook implements ObjectStorageLifecycleHook {
        private final List<String> events

        private RecordingHook(List<String> events) {
            this.events = events
        }

        @Override
        void after(ObjectStorageOperationContext context, ObjectStorageOperationOutcome outcome) {
            assert context.objectKey == '/2026/03/invoice-001.pdf'
            assert outcome.reconciliationId.orElse('missing') == 'recon-1'
            events << "after:${context.tenantId}:${context.operationType}:${context.objectKey}:${outcome.reconciliationId.orElse('missing')}".toString()
        }
    }

    private static final class FixedDescriptorResolver implements StorageDescriptorResolver {
        private final StorageDescriptor descriptor

        private FixedDescriptorResolver(StorageDescriptor descriptor) {
            this.descriptor = descriptor
        }

        @Override
        StorageDescriptor resolve() {
            descriptor
        }
    }

    private static final class InMemoryObjectMetadataRepository implements StorageObjectMetadataRepository {
        private final Map<String, StorageObjectMetadataRecord> rows = [:]

        @Override
        void upsert(StorageObjectMetadata metadata, Map<String, String> attributes) {
            rows.put(key(metadata.tenantId, metadata.storageName, metadata.logicalContainer, metadata.objectKeyHash), new StorageObjectMetadataRecord(metadata, attributes))
        }

        @Override
        Optional<StorageObjectMetadataRecord> findByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
            Optional.ofNullable(rows.get(key(tenantId, storageDescriptor.storageName, storageDescriptor.logicalContainer, objectKeyHash)))
        }

        @Override
        List<StorageObjectMetadataRecord> listByTenantAndQuery(String tenantId, StorageObjectMetadataQuery query) {
            []
        }

        @Override
        void deleteByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
            rows.remove(key(tenantId, storageDescriptor.storageName, storageDescriptor.logicalContainer, objectKeyHash))
        }

        private static String key(String tenantId, String storageName, String logicalContainer, String objectKeyHash) {
            "${tenantId}|${storageName}|${logicalContainer}|${objectKeyHash}"
        }
    }

    private static final class InMemoryOutboxRepository implements StorageMetadataOutboxRepository {
        private final Map<String, StorageMetadataOutboxRecord> rowsById = [:]
        private final Map<String, String> rowIdsByIdempotency = [:]

        @Override
        void enqueue(StorageMetadataOutboxRecord outboxRecord) {
            if (!enqueueIfAbsent(outboxRecord)) {
                throw new IllegalStateException('duplicate idempotency key')
            }
        }

        @Override
        boolean enqueueIfAbsent(StorageMetadataOutboxRecord outboxRecord) {
            if (rowIdsByIdempotency.containsKey(outboxRecord.idempotencyKey)) {
                return false
            }
            rowIdsByIdempotency.put(outboxRecord.idempotencyKey, outboxRecord.id)
            rowsById.put(outboxRecord.id, outboxRecord)
            true
        }

        @Override
        List<StorageMetadataOutboxRecord> findDueEntries(Instant asOf, int limit) {
            rowsById.values()
                .findAll { it.nextAttemptAt <= asOf && it.status in [StorageMetadataOutboxStatus.PENDING, StorageMetadataOutboxStatus.FAILED, StorageMetadataOutboxStatus.PROCESSING] }
                .sort { it.nextAttemptAt }
                .take(limit)
        }

        @Override
        boolean markProcessing(String id, Instant asOf, Instant updatedAt) {
            StorageMetadataOutboxRecord existing = rowsById.get(id)
            if (existing == null || existing.nextAttemptAt > asOf || !(existing.status in [StorageMetadataOutboxStatus.PENDING, StorageMetadataOutboxStatus.FAILED, StorageMetadataOutboxStatus.PROCESSING])) {
                return false
            }
            rowsById.put(id, withStatus(existing, StorageMetadataOutboxStatus.PROCESSING, existing.attemptCount, existing.nextAttemptAt, existing.lastErrorSummary.orElse(null), updatedAt))
            true
        }

        @Override
        void markSucceeded(String id, Instant updatedAt) {
            StorageMetadataOutboxRecord existing = rowsById.get(id)
            rowsById.put(id, withStatus(existing, StorageMetadataOutboxStatus.SUCCEEDED, existing.attemptCount, existing.nextAttemptAt, null, updatedAt))
        }

        @Override
        void markRetryOrDeadLetter(String id, int attemptCount, Instant nextAttemptAt, StorageMetadataOutboxStatus status, String lastErrorSummary, Instant updatedAt) {
            StorageMetadataOutboxRecord existing = rowsById.get(id)
            rowsById.put(id, withStatus(existing, status, attemptCount, nextAttemptAt, lastErrorSummary, updatedAt))
        }

        @Override
        Optional<StorageMetadataOutboxRecord> findById(String id) {
            Optional.ofNullable(rowsById.get(id))
        }

        @Override
        Optional<StorageMetadataOutboxRecord> findByIdempotencyKey(String idempotencyKey) {
            Optional.ofNullable(rowIdsByIdempotency.get(idempotencyKey)).flatMap { id -> findById(id) }
        }

        private static StorageMetadataOutboxRecord withStatus(StorageMetadataOutboxRecord record,
                                                              StorageMetadataOutboxStatus status,
                                                              int attemptCount,
                                                              Instant nextAttemptAt,
                                                              String lastErrorSummary,
                                                              Instant updatedAt) {
            new StorageMetadataOutboxRecord(
                record.id,
                record.tenantId,
                record.storageName,
                record.logicalContainer,
                record.objectKey.orElse(null),
                record.objectKeyHash.orElse(null),
                record.destinationObjectKey.orElse(null),
                record.destinationObjectKeyHash.orElse(null),
                record.operationType,
                record.reconciliationId.orElse(null),
                record.payloadVersion,
                status,
                attemptCount,
                nextAttemptAt,
                lastErrorSummary,
                record.idempotencyKey,
                record.createdAt,
                updatedAt
            )
        }
    }
}
