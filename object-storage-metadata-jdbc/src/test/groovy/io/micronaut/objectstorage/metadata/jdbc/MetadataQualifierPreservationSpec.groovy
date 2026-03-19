package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

class MetadataQualifierPreservationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'MetadataQualifierPreservationSpec'])

    void '@Named metadata beans preserve the requested qualifier identity'() {
        expect:
        context.getBean(io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations, Qualifiers.byName('default')) != null
        context.getBean(io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations, Qualifiers.byName('pictures')) != null

        and:
        context.findBean(ObjectStorageOperations, Qualifiers.byName('default')).present
        context.findBean(ObjectStorageOperations, Qualifiers.byName('pictures')).present
        context.findBean(ObjectStorageMetadataOperations, Qualifiers.byName('default')).present
        context.findBean(ObjectStorageMetadataOperations, Qualifiers.byName('pictures')).present
    }

    void 'raw and metadata beans resolve for the same qualifier'() {
        expect:
        context.getBean(ObjectStorageOperations, Qualifiers.byName('default')).class.simpleName == 'StubObjectStorageOperations'
        context.getBean(ObjectStorageOperations, Qualifiers.byName('pictures')).class.simpleName == 'StubObjectStorageOperations'

        and:
        context.getBean(ObjectStorageMetadataOperations, Qualifiers.byName('default')).activeTenantId == 'tenant-a'
        context.getBean(ObjectStorageMetadataOperations, Qualifiers.byName('pictures')).activeTenantId == 'tenant-a'

        and:
        context.getBean(MetadataAwareObjectStorageOperations, Qualifiers.byName('default')).metadataOperations
            .is(context.getBean(ObjectStorageMetadataOperations, Qualifiers.byName('default')))
        context.getBean(MetadataAwareObjectStorageOperations, Qualifiers.byName('pictures')).metadataOperations
            .is(context.getBean(ObjectStorageMetadataOperations, Qualifiers.byName('pictures')))

        and:
        def defaultMetadata = context.getBean(ObjectStorageMetadataOperations, Qualifiers.byName('default'))
        def picturesMetadata = context.getBean(ObjectStorageMetadataOperations, Qualifiers.byName('pictures'))
        defaultMetadata.findContainer(new StorageDescriptor('default', 'test', 'default', null, 'default')).empty
        picturesMetadata.findContainer(new StorageDescriptor('pictures', 'test', 'pictures', null, 'pictures')).present
        picturesMetadata.findObject(new StorageDescriptor('pictures', 'test', 'pictures', null, 'pictures'), '/logo.png').present
        picturesMetadata.listObjects(StorageObjectMetadataQuery.all().withStorageName('pictures'))*.objectKey == ['/logo.png']
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MetadataQualifierPreservationSpec')
    static class TestTenantResolver implements TenantResolver {
        @Override
        String resolveTenantId() {
            return 'tenant-a'
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'MetadataQualifierPreservationSpec')
    static class TestRepositories {
        @Singleton
        @Named('default')
        ObjectStorageOperations defaultOperations() {
            new StubObjectStorageOperations()
        }

        @Singleton
        @Named('pictures')
        ObjectStorageOperations picturesOperations() {
            new StubObjectStorageOperations()
        }

        @Singleton
        @Named('default')
        io.micronaut.objectstorage.metadata.StorageDescriptorResolver defaultDescriptorResolver() {
            new FixedDescriptorResolver(new StorageDescriptor('default', 'test', 'default', null, 'default'))
        }

        @Singleton
        @Named('pictures')
        io.micronaut.objectstorage.metadata.StorageDescriptorResolver picturesDescriptorResolver() {
            new FixedDescriptorResolver(new StorageDescriptor('pictures', 'test', 'pictures', null, 'pictures'))
        }

        @Singleton
        StorageContainerMetadataRepository storageContainerMetadataRepository() {
            new StubStorageContainerMetadataRepository()
        }

        @Singleton
        StorageObjectMetadataRepository storageObjectMetadataRepository() {
            new StubStorageObjectMetadataRepository()
        }

        @Singleton
        StorageMetadataOutboxRepository storageMetadataOutboxRepository() {
            new StubStorageMetadataOutboxRepository()
        }
    }

    static class FixedDescriptorResolver implements io.micronaut.objectstorage.metadata.StorageDescriptorResolver {
        private final StorageDescriptor descriptor

        FixedDescriptorResolver(StorageDescriptor descriptor) {
            this.descriptor = descriptor
        }

        @Override
        StorageDescriptor resolve() {
            descriptor
        }
    }

    static class StubStorageContainerMetadataRepository implements StorageContainerMetadataRepository {
        @Override
        Optional<StorageContainerMetadataRecord> findByTenantAndDescriptor(String tenantId, StorageDescriptor descriptor) {
            if (tenantId == 'tenant-a' && descriptor.storageName == 'pictures' && descriptor.logicalContainer == 'pictures') {
                return Optional.of(new StorageContainerMetadataRecord(new StorageContainerMetadata(tenantId, descriptor, java.time.Instant.parse('2026-03-17T12:00:00Z'), java.time.Instant.parse('2026-03-17T12:00:00Z')), [kind: 'images']))
            }
            Optional.empty()
        }

        @Override
        void upsert(StorageContainerMetadata metadata, Map<String, String> attributes) {
        }
    }

    static class StubStorageObjectMetadataRepository implements StorageObjectMetadataRepository {
        private final StorageObjectMetadataRecord picturesRecord = new StorageObjectMetadataRecord(
            new StorageObjectMetadata('tenant-a', new StorageDescriptor('pictures', 'test', 'pictures', null, 'pictures'), '/logo.png', StorageObjectMetadata.deterministicObjectKeyHash('/logo.png'), 'image/png', 12L, 'etag-1', null, null, java.time.Instant.parse('2026-03-17T12:00:00Z'), java.time.Instant.parse('2026-03-17T12:00:00Z'), new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)),
            [owner: 'marketing']
        )

        @Override
        Optional<StorageObjectMetadataRecord> findByTenantAndObjectHash(String tenantId, StorageDescriptor descriptor, String objectKeyHash) {
            if (tenantId == 'tenant-a' && descriptor.storageName == 'pictures' && descriptor.logicalContainer == 'pictures' && objectKeyHash == picturesRecord.metadata().objectKeyHash) {
                return Optional.of(picturesRecord)
            }
            Optional.empty()
        }

        @Override
        List<StorageObjectMetadataRecord> listByTenantAndQuery(String tenantId, StorageObjectMetadataQuery query) {
            if (tenantId != 'tenant-a') {
                return []
            }
            return [picturesRecord].findAll { record ->
                (!query.storageName.present || query.storageName.get() == record.metadata().storageName) &&
                (!query.logicalContainer.present || query.logicalContainer.get() == record.metadata().logicalContainer) &&
                (!query.objectKeyPrefix.present || record.metadata().objectKey.startsWith(query.objectKeyPrefix.get()))
            }
        }

        @Override
        void upsert(StorageObjectMetadata metadata, Map<String, String> attributes) {
        }

        @Override
        void deleteByTenantAndObjectHash(String tenantId, StorageDescriptor storageDescriptor, String objectKeyHash) {
        }
    }

    static class StubStorageMetadataOutboxRepository implements StorageMetadataOutboxRepository {
        private final Map<String, StorageMetadataOutboxRecord> rows = [:]

        @Override
        void enqueue(StorageMetadataOutboxRecord outboxRecord) {
            rows[outboxRecord.id] = outboxRecord
        }

        @Override
        boolean enqueueIfAbsent(StorageMetadataOutboxRecord outboxRecord) {
            if (rows.containsKey(outboxRecord.id)) {
                return false
            }
            rows[outboxRecord.id] = outboxRecord
            true
        }

        @Override
        List<StorageMetadataOutboxRecord> findDueEntries(java.time.Instant asOf, int limit) { [] }

        @Override
        boolean markProcessing(String id, java.time.Instant asOf, java.time.Instant updatedAt) { false }

        @Override
        void markSucceeded(String id, java.time.Instant updatedAt) { }

        @Override
        void markRetryOrDeadLetter(String id, int attemptCount, java.time.Instant nextAttemptAt, io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus status, String lastErrorSummary, java.time.Instant updatedAt) { }

        @Override
        Optional<StorageMetadataOutboxRecord> findById(String id) { Optional.ofNullable(rows[id]) }

        @Override
        Optional<StorageMetadataOutboxRecord> findByIdempotencyKey(String idempotencyKey) { Optional.empty() }
    }

    static class StubObjectStorageOperations implements ObjectStorageOperations<Object, Object, Object> {
        @Override
        io.micronaut.objectstorage.response.UploadResponse<Object> upload(io.micronaut.objectstorage.request.UploadRequest request) { throw new UnsupportedOperationException() }
        @Override
        io.micronaut.objectstorage.response.UploadResponse<Object> upload(io.micronaut.objectstorage.request.UploadRequest request, java.util.function.Consumer<Object> requestConsumer) { throw new UnsupportedOperationException() }
        @Override
        java.util.Optional retrieve(String key) { java.util.Optional.empty() }
        @Override
        Object delete(String key) { null }
    }
}
