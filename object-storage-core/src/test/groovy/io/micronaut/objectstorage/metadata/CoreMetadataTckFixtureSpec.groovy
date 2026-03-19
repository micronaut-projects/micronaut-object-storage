package io.micronaut.objectstorage.metadata

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class CoreMetadataTckFixtureSpec extends ObjectStorageMetadataSpecification {

    @Override
    ObjectStorageMetadataSpecification.MetadataTckFixture getMetadataFixture() {
        return new InMemoryMetadataTckFixture()
    }

    private static class InMemoryMetadataTckFixture extends ObjectStorageMetadataSpecification.MetadataTckFixture {
        private static final Instant NOW = Instant.parse('2026-03-17T16:30:00Z')
        private final StorageDescriptor primaryStorageDescriptor = new StorageDescriptor('primary-storage', 'test-provider', 'avatars', 'tenant-space', 'bucket-a')
        private final StorageDescriptor secondaryStorageDescriptor = new StorageDescriptor('secondary-storage', 'test-provider', 'reports', 'tenant-space', 'bucket-b')
        private final MutableTenantResolver tenantResolver = new MutableTenantResolver('tenant-alpha')
        private final InMemoryMetadataOperations metadataOperations = new InMemoryMetadataOperations(tenantResolver)
        private final List<ObjectStorageMetadataSpecification.LifecycleEvent> lifecycleEvents = []
        private final Map<String, String> reconciliationIds = [:]

        @Override
        ObjectStorageMetadataOperations getMetadataOperations() {
            return metadataOperations
        }

        @Override
        StorageDescriptor getPrimaryStorageDescriptor() {
            return primaryStorageDescriptor
        }

        @Override
        StorageDescriptor getSecondaryStorageDescriptor() {
            return secondaryStorageDescriptor
        }

        @Override
        void seedPendingMetadata(StorageObjectMetadata metadata) {
            metadataOperations.seedPending(metadata)
        }

        @Override
        void seedSucceededMetadata(StorageObjectMetadata metadata) {
            metadataOperations.seedSucceeded(metadata)
        }

        @Override
        UploadResponse<?> upload(StorageDescriptor descriptor, UploadRequest uploadRequest) {
            return doUpload(descriptor, uploadRequest, false)
        }

        @Override
        UploadResponse<?> uploadWithError(StorageDescriptor descriptor, UploadRequest uploadRequest) {
            return doUpload(descriptor, uploadRequest, true)
        }

        @Override
        List<ObjectStorageMetadataSpecification.LifecycleEvent> getLifecycleEvents() {
            return lifecycleEvents
        }

        @Override
        String reconciliationIdFor(String objectKey) {
            return reconciliationIds[objectKey]
        }

        @Override
        def <T> T withTenant(String tenantId, Closure<T> callable) {
            String previous = tenantResolver.currentTenant
            tenantResolver.currentTenant = tenantId
            try {
                return callable.call()
            } finally {
                tenantResolver.currentTenant = previous
            }
        }

        private UploadResponse<?> doUpload(StorageDescriptor descriptor, UploadRequest uploadRequest, boolean failDispatch) {
            lifecycleEvents.clear()
            ObjectStorageOperationContext context = ObjectStorageOperationContext.forUpload(tenantResolver.resolveTenantId(), descriptor, uploadRequest)
            List<NamedHook> hooks = ObjectStorageLifecycleHook.ordered([
                new NamedHook('low-precedence', 100, lifecycleEvents),
                new NamedHook('high-precedence', -100, lifecycleEvents)
            ]) as List<NamedHook>
            hooks.each { it.before(context) }
            String reconciliationId = "rec-${uploadRequest.key}"
            reconciliationIds[uploadRequest.key] = reconciliationId
            UploadResponse<?> response = UploadResponse.of(uploadRequest.key, "etag-${uploadRequest.key}", 'native-upload')
            ObjectStorageOperationOutcome outcome = ObjectStorageOperationOutcome.uploadSuccess(context, response, reconciliationId)
            if (failDispatch) {
                MetadataDispatchException exception = new MetadataDispatchException(context, outcome, reconciliationId, new IllegalStateException('metadata dispatch failed'))
                hooks.first().error(context, exception)
                throw exception
            }
            hooks.each { it.after(context, outcome) }
            metadataOperations.scheduleVisibility(succeededMetadata(context.tenantId, descriptor, uploadRequest.key))
            return response
        }

        private static StorageObjectMetadata succeededMetadata(String tenantId, StorageDescriptor descriptor, String objectKey) {
            return new StorageObjectMetadata(
                tenantId,
                descriptor,
                objectKey,
                null,
                CONTENT_TYPE,
                TEXT.length() as Long,
                "etag-${objectKey}",
                'v1',
                null,
                NOW,
                NOW,
                new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
            )
        }
    }

    private static final class NamedHook implements ObjectStorageLifecycleHook {
        private final String hookName
        private final int order
        private final List<ObjectStorageMetadataSpecification.LifecycleEvent> lifecycleEvents

        NamedHook(String hookName, int order, List<ObjectStorageMetadataSpecification.LifecycleEvent> lifecycleEvents) {
            this.hookName = hookName
            this.order = order
            this.lifecycleEvents = lifecycleEvents
        }

        @Override
        int getOrder() {
            return order
        }

        @Override
        void before(ObjectStorageOperationContext context) {
            lifecycleEvents << new ObjectStorageMetadataSpecification.LifecycleEvent('before', hookName, context)
        }

        @Override
        void after(ObjectStorageOperationContext context, ObjectStorageOperationOutcome outcome) {
            lifecycleEvents << new ObjectStorageMetadataSpecification.LifecycleEvent('after', hookName, context, outcome)
        }

        @Override
        void error(ObjectStorageOperationContext context, Throwable throwable) {
            lifecycleEvents << new ObjectStorageMetadataSpecification.LifecycleEvent('error', hookName, context, null, throwable)
        }
    }

    private static final class MutableTenantResolver implements TenantResolver {
        String currentTenant

        MutableTenantResolver(String currentTenant) {
            this.currentTenant = currentTenant
        }

        @Override
        String resolveTenantId() {
            return currentTenant
        }
    }

    private static final class InMemoryMetadataOperations implements ObjectStorageMetadataOperations {
        private static final Instant CONTAINER_NOW = Instant.parse('2026-03-17T16:30:00Z')
        private final TenantResolver tenantResolver
        private final Map<String, List<StorageObjectMetadata>> visible = new ConcurrentHashMap<>()
        private final Map<String, List<StorageObjectMetadata>> pending = new ConcurrentHashMap<>()

        InMemoryMetadataOperations(TenantResolver tenantResolver) {
            this.tenantResolver = tenantResolver
        }

        void seedPending(StorageObjectMetadata metadata) {
            pending.compute(metadata.tenantId) { key, entries -> ((entries ?: []) + metadata) as List<StorageObjectMetadata> }
        }

        void seedSucceeded(StorageObjectMetadata metadata) {
            visible.compute(metadata.tenantId) { key, entries -> ((entries ?: []) + metadata) as List<StorageObjectMetadata> }
        }

        void scheduleVisibility(StorageObjectMetadata metadata) {
            seedPending(metadata)
            Thread.startDaemon {
                sleep 150
                pending.computeIfPresent(metadata.tenantId) { key, entries -> entries.findAll { it.objectKey != metadata.objectKey } }
                seedSucceeded(metadata)
            }
        }

        @Override
        TenantResolver getTenantResolver() {
            return tenantResolver
        }

        @Override
        Optional<StorageContainerMetadata> findContainer(StorageDescriptor storageDescriptor) {
            List<StorageObjectMetadata> scoped = visibleForActiveTenant().findAll { sameContainer(it, storageDescriptor) }
            if (scoped.isEmpty()) {
                return Optional.empty()
            }
            return Optional.of(new StorageContainerMetadata(getActiveTenantId(), storageDescriptor, CONTAINER_NOW, CONTAINER_NOW))
        }

        @Override
        Optional<StorageObjectMetadata> findObject(StorageDescriptor storageDescriptor, String objectKey) {
            return Optional.ofNullable(visibleForActiveTenant().find { sameContainer(it, storageDescriptor) && it.objectKey == objectKey })
        }

        @Override
        List<StorageObjectMetadata> listObjects(StorageObjectMetadataQuery query) {
            return visibleForActiveTenant().findAll { metadata ->
                boolean storageMatches = query.storageName.map { it == metadata.storageName }.orElse(true)
                boolean containerMatches = query.logicalContainer.map { it == metadata.logicalContainer }.orElse(true)
                boolean prefixMatches = query.objectKeyPrefix.map { metadata.objectKey.startsWith(it) }.orElse(true)
                return storageMatches && containerMatches && prefixMatches
            }.sort { a, b -> a.objectKey <=> b.objectKey }
        }

        private List<StorageObjectMetadata> visibleForActiveTenant() {
            return visible.getOrDefault(getActiveTenantId(), [])
        }

        private static boolean sameContainer(StorageObjectMetadata metadata, StorageDescriptor storageDescriptor) {
            return metadata.storageName == storageDescriptor.storageName &&
                metadata.logicalContainer == storageDescriptor.logicalContainer
        }
    }
}
