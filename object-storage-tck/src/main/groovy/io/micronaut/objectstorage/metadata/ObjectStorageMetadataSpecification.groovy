/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage.metadata

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

abstract class ObjectStorageMetadataSpecification extends Specification {

    static final String TEXT = 'micronaut'
    static final Map<String, String> METADATA = [project: 'micronaut-object-storage']
    static final String CONTENT_TYPE = 'text/plain'

    private static final Instant NOW = Instant.parse('2026-03-17T16:00:00Z')

    void 'it resolves exact lookups, prefix listings, scoping, ordered hooks and reconciliation visibility'() {
        given:
        MetadataTckFixture fixture = getMetadataFixture()
        PollingConditions conditions = new PollingConditions(timeout: 10)
        StorageDescriptor primary = fixture.primaryStorageDescriptor
        StorageDescriptor secondary = fixture.secondaryStorageDescriptor

        and:
        fixture.seedPendingMetadata(metadata(primary, 'tenant-alpha', 'images/avatar.png', ObjectMetadataReconciliationState.PENDING))
        fixture.seedSucceededMetadata(metadata(primary, 'tenant-alpha', 'images/banner.png', ObjectMetadataReconciliationState.SUCCEEDED))
        fixture.seedSucceededMetadata(metadata(primary, 'tenant-beta', 'images/avatar.png', ObjectMetadataReconciliationState.SUCCEEDED))
        fixture.seedSucceededMetadata(metadata(secondary, 'tenant-alpha', 'images/avatar.png', ObjectMetadataReconciliationState.SUCCEEDED))

        when:
        Optional<StorageObjectMetadata> exactMatch = fixture.withTenant('tenant-alpha') {
            fixture.metadataOperations.findObject(primary, 'images/avatar.png')
        }

        then:
        !exactMatch.present

        when:
        List<StorageObjectMetadata> tenantAlphaPrefixMatches = fixture.withTenant('tenant-alpha') {
            fixture.metadataOperations.listObjects(StorageObjectMetadataQuery.all()
                .withStorageName(primary.storageName)
                .withLogicalContainer(primary.logicalContainer)
                .withObjectKeyPrefix('images/'))
        }
        List<StorageObjectMetadata> tenantBetaPrefixMatches = fixture.withTenant('tenant-beta') {
            fixture.metadataOperations.listObjects(StorageObjectMetadataQuery.all()
                .withStorageName(primary.storageName)
                .withLogicalContainer(primary.logicalContainer)
                .withObjectKeyPrefix('images/'))
        }
        List<StorageObjectMetadata> tenantAlphaSecondaryMatches = fixture.withTenant('tenant-alpha') {
            fixture.metadataOperations.listObjects(StorageObjectMetadataQuery.all()
                .withStorageName(secondary.storageName)
                .withLogicalContainer(secondary.logicalContainer)
                .withObjectKeyPrefix('images/'))
        }

        then:
        tenantAlphaPrefixMatches*.objectKey == ['images/banner.png']
        tenantBetaPrefixMatches*.tenantId == ['tenant-beta']
        tenantBetaPrefixMatches*.objectKey == ['images/avatar.png']
        tenantAlphaSecondaryMatches*.storageName == [secondary.storageName]
        tenantAlphaSecondaryMatches*.logicalContainer == [secondary.logicalContainer]

        when:
        UploadRequest uploadRequest = UploadRequest.fromBytes(TEXT.bytes, 'images/avatar.png', CONTENT_TYPE)
        uploadRequest.metadata = METADATA
        UploadResponse<?> uploadResponse = fixture.withTenant('tenant-alpha') {
            fixture.upload(primary, uploadRequest)
        }

        then:
        uploadResponse.key == 'images/avatar.png'
        fixture.lifecycleEvents*.phase == ['before', 'before', 'after', 'after']
        fixture.lifecycleEvents*.hookName == ['high-precedence', 'low-precedence', 'high-precedence', 'low-precedence']
        fixture.lifecycleEvents.every { it.context.tenantId == 'tenant-alpha' }
        fixture.lifecycleEvents.every { it.context.storageDescriptor.storageName == primary.storageName }
        fixture.lifecycleEvents.every { it.context.objectKey == 'images/avatar.png' }
        fixture.lifecycleEvents.findAll { it.phase == 'after' }.every {
            it.outcome.reconciliationId.present && it.outcome.reconciliationId.get() == fixture.reconciliationIdFor('images/avatar.png')
        }

        when:
        Optional<StorageObjectMetadata> immediateLookup = fixture.withTenant('tenant-alpha') {
            fixture.metadataOperations.findObject(primary, 'images/avatar.png')
        }

        then:
        !immediateLookup.present

        when:
        conditions.eventually {
            assert fixture.withTenant('tenant-alpha') {
                fixture.metadataOperations.findObject(primary, 'images/avatar.png')
            }.present
        }
        Optional<StorageObjectMetadata> reconciledLookup = fixture.withTenant('tenant-alpha') {
            fixture.metadataOperations.findObject(primary, 'images/avatar.png')
        }

        then:
        reconciledLookup.present
        reconciledLookup.get().tenantId == 'tenant-alpha'
        reconciledLookup.get().storageName == primary.storageName
        reconciledLookup.get().logicalContainer == primary.logicalContainer
        reconciledLookup.get().objectKey == 'images/avatar.png'
        reconciledLookup.get().contentType.get() == CONTENT_TYPE
        reconciledLookup.get().reconciliationStatus == ObjectMetadataReconciliationState.SUCCEEDED

        when:
        StorageContainerMetadata container = fixture.withTenant('tenant-alpha') {
            fixture.metadataOperations.findContainer(primary).orElseThrow()
        }

        then:
        container.tenantId == 'tenant-alpha'
        container.storageDescriptor.storageName == primary.storageName
    }

    void 'it dispatches hook failures after partial completion'() {
        given:
        MetadataTckFixture fixture = getMetadataFixture()
        StorageDescriptor primary = fixture.primaryStorageDescriptor
        UploadRequest uploadRequest = UploadRequest.fromBytes(TEXT.bytes, 'images/failure.png', CONTENT_TYPE)
        uploadRequest.metadata = METADATA

        when:
        fixture.withTenant('tenant-alpha') {
            fixture.uploadWithError(primary, uploadRequest)
        }

        then:
        MetadataDispatchException e = thrown()
        e.operationContext.tenantId == 'tenant-alpha'
        e.operationContext.storageDescriptor.storageName == primary.storageName
        e.operationContext.objectKey == 'images/failure.png'
        e.operationOutcome.operationType == ObjectStorageOperationType.UPLOAD
        e.operationOutcome.objectKey == 'images/failure.png'
        e.reconciliationId.present
        fixture.lifecycleEvents*.phase == ['before', 'before', 'error']
        fixture.lifecycleEvents*.hookName == ['high-precedence', 'low-precedence', 'high-precedence']
        fixture.lifecycleEvents.last().throwable.is(e)
    }

    abstract MetadataTckFixture getMetadataFixture()

    static Path createTempFile() {
        Path path = Files.createTempFile('test-file', '.txt')
        path.toFile().text = TEXT
        path
    }

    private static StorageObjectMetadata metadata(StorageDescriptor descriptor, String tenantId, String objectKey, ObjectMetadataReconciliationState state) {
        new StorageObjectMetadata(
            tenantId,
            descriptor,
            objectKey,
            null,
            CONTENT_TYPE,
            TEXT.length() as Long,
            "etag-${tenantId}-${objectKey}",
            'v1',
            null,
            NOW,
            NOW,
            new ObjectMetadataSyncStatus(state, null)
        )
    }

    static abstract class MetadataTckFixture {
        abstract ObjectStorageMetadataOperations getMetadataOperations()

        abstract StorageDescriptor getPrimaryStorageDescriptor()

        abstract StorageDescriptor getSecondaryStorageDescriptor()

        abstract void seedPendingMetadata(StorageObjectMetadata metadata)

        abstract void seedSucceededMetadata(StorageObjectMetadata metadata)

        abstract UploadResponse<?> upload(StorageDescriptor descriptor, UploadRequest uploadRequest)

        abstract UploadResponse<?> uploadWithError(StorageDescriptor descriptor, UploadRequest uploadRequest)

        abstract List<LifecycleEvent> getLifecycleEvents()

        abstract String reconciliationIdFor(String objectKey)

        abstract <T> T withTenant(String tenantId, Closure<T> callable)
    }

    static final class LifecycleEvent {
        final String phase
        final String hookName
        final ObjectStorageOperationContext context
        final ObjectStorageOperationOutcome outcome
        final Throwable throwable

        LifecycleEvent(String phase,
                       String hookName,
                       ObjectStorageOperationContext context,
                       ObjectStorageOperationOutcome outcome = null,
                       Throwable throwable = null) {
            this.phase = phase
            this.hookName = hookName
            this.context = context
            this.outcome = outcome
            this.throwable = throwable
        }
    }
}
