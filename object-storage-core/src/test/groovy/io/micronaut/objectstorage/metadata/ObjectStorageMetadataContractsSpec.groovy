package io.micronaut.objectstorage.metadata

import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.time.Instant

class ObjectStorageMetadataContractsSpec extends Specification {

    private static final Instant NOW = Instant.parse("2026-03-17T14:00:00Z")

    void "public metadata queries resolve tenant from tenant resolver"() {
        given:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "aws-s3", "profiles", "ns-a", "bucket-a")
        TrackingTenantResolver tenantResolver = new TrackingTenantResolver("tenant-a")
        InMemoryMetadataOperations operations = new InMemoryMetadataOperations(tenantResolver)
        operations.addObject(sampleMetadata("tenant-a", descriptor, "/profiles/avatar.png"))
        operations.addObject(sampleMetadata("tenant-b", descriptor, "/profiles/avatar.png"))

        when:
        Optional<StorageObjectMetadata> tenantA = operations.findObject(descriptor, "/profiles/avatar.png")
        tenantResolver.currentTenant = "tenant-b"
        Optional<StorageObjectMetadata> tenantB = operations.findObject(descriptor, "/profiles/avatar.png")

        then:
        tenantA.present
        tenantA.get().tenantId == "tenant-a"
        tenantB.present
        tenantB.get().tenantId == "tenant-b"
        tenantResolver.resolveCount == 2

        and:
        ObjectStorageMetadataOperations.declaredMethods.find { it.name == "findContainer" }.parameterCount == 1
        ObjectStorageMetadataOperations.declaredMethods.find { it.name == "findObject" }.parameterCount == 2
        ObjectStorageMetadataOperations.declaredMethods.find { it.name == "listObjects" }.parameterCount == 1
    }

    void "metadata dispatch exception keeps partial-completion context"() {
        given:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "aws-s3", "profiles", "ns-a", "bucket-a")
        def uploadRequest = io.micronaut.objectstorage.request.UploadRequest.fromBytes("abc".bytes, "/profiles/avatar.png", "image/png")
        ObjectStorageOperationContext context = ObjectStorageOperationContext.forUpload("tenant-a", descriptor, uploadRequest)
        ObjectStorageOperationOutcome outcome = ObjectStorageOperationOutcome.uploadSuccess(
            context,
            UploadResponse.of("/profiles/avatar.png", "etag-1", "native-upload"),
            "rec-1"
        )
        RuntimeException dispatchFailure = new RuntimeException("outbox insert failed")

        when:
        MetadataDispatchException exception = new MetadataDispatchException(context, outcome, "rec-1", dispatchFailure)

        then:
        exception.cause.is(dispatchFailure)
        exception.message.contains("UPLOAD")
        exception.message.contains("tenant-a")
        exception.message.contains("pictures")
        exception.message.contains("/profiles/avatar.png")
        exception.reconciliationId.present
        exception.reconciliationId.get() == "rec-1"
        exception.operationContext.operationType == ObjectStorageOperationType.UPLOAD
        exception.operationContext.storageDescriptor.storageName == "pictures"
        exception.operationOutcome.operationType == ObjectStorageOperationType.UPLOAD
        exception.operationOutcome.tenantId == "tenant-a"
        exception.operationOutcome.storageDescriptor.storageName == "pictures"
        exception.operationOutcome.uploadResponse.present
        exception.operationOutcome.uploadResponse.get().key == "/profiles/avatar.png"
        exception.operationOutcome.reconciliationId.present
        exception.operationOutcome.reconciliationId.get() == "rec-1"
    }

    void "operation context factories cover upload copy and delete"() {
        given:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "aws-s3", "profiles", "ns-a", "bucket-a")
        def uploadRequest = io.micronaut.objectstorage.request.UploadRequest.fromBytes("abc".bytes, "/profiles/avatar.png", "image/png")

        when:
        ObjectStorageOperationContext uploadContext = ObjectStorageOperationContext.forUpload("tenant-a", descriptor, uploadRequest)
        ObjectStorageOperationContext copyContext = ObjectStorageOperationContext.forCopy("tenant-a", descriptor, "/profiles/avatar.png", "/profiles/avatar-copy.png")
        ObjectStorageOperationContext deleteContext = ObjectStorageOperationContext.forDelete("tenant-a", descriptor, "/profiles/avatar.png")

        then:
        uploadContext.operationType == ObjectStorageOperationType.UPLOAD
        uploadContext.destinationObjectKey.empty
        uploadContext.uploadRequest.present

        and:
        copyContext.operationType == ObjectStorageOperationType.COPY
        copyContext.destinationObjectKey.present
        copyContext.destinationObjectKey.get() == "/profiles/avatar-copy.png"
        copyContext.uploadRequest.empty

        and:
        deleteContext.operationType == ObjectStorageOperationType.DELETE
        deleteContext.destinationObjectKey.empty
        deleteContext.uploadRequest.empty
    }

    private static StorageObjectMetadata sampleMetadata(String tenantId, StorageDescriptor descriptor, String objectKey) {
        new StorageObjectMetadata(
            tenantId,
            descriptor,
            objectKey,
            null,
            "image/png",
            3L,
            "etag-1",
            "v1",
            null,
            NOW,
            NOW,
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        )
    }

    private static final class TrackingTenantResolver implements TenantResolver {
        String currentTenant
        int resolveCount

        TrackingTenantResolver(String currentTenant) {
            this.currentTenant = currentTenant
        }

        @Override
        String resolveTenantId() {
            resolveCount++
            return currentTenant
        }
    }

    private static final class InMemoryMetadataOperations implements ObjectStorageMetadataOperations {
        private final TenantResolver tenantResolver
        private final Map<String, List<StorageObjectMetadata>> byTenant = [:].withDefault { [] }

        InMemoryMetadataOperations(TenantResolver tenantResolver) {
            this.tenantResolver = tenantResolver
        }

        void addObject(StorageObjectMetadata metadata) {
            byTenant[metadata.tenantId] = byTenant[metadata.tenantId] + metadata
        }

        @Override
        TenantResolver getTenantResolver() {
            return tenantResolver
        }

        @Override
        Optional<StorageContainerMetadata> findContainer(StorageDescriptor storageDescriptor) {
            List<StorageObjectMetadata> objects = byTenant[getActiveTenantId()].findAll { metadata ->
                metadata.storageDescriptor.storageName == storageDescriptor.storageName &&
                    metadata.logicalContainer == storageDescriptor.logicalContainer
            }
            if (objects.isEmpty()) {
                return Optional.empty()
            }
            return Optional.of(new StorageContainerMetadata(getActiveTenantId(), storageDescriptor, NOW, NOW))
        }

        @Override
        Optional<StorageObjectMetadata> findObject(StorageDescriptor storageDescriptor, String objectKey) {
            return Optional.ofNullable(byTenant[getActiveTenantId()]
                .find { metadata -> metadata.storageDescriptor.storageName == storageDescriptor.storageName && metadata.objectKey == objectKey })
        }

        @Override
        List<StorageObjectMetadata> listObjects(StorageObjectMetadataQuery query) {
            return byTenant[getActiveTenantId()].findAll { metadata ->
                boolean matchesStorage = query.storageName.map { it == metadata.storageDescriptor.storageName }.orElse(true)
                boolean matchesContainer = query.logicalContainer.map { it == metadata.logicalContainer }.orElse(true)
                boolean matchesPrefix = query.objectKeyPrefix.map { metadata.objectKey.startsWith(it) }.orElse(true)
                return matchesStorage && matchesContainer && matchesPrefix
            }
        }
    }
}
