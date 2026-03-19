package io.micronaut.objectstorage.metadata

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.util.concurrent.PollingConditions

class MetadataHookFailureFixtureSpec extends CoreMetadataTckFixtureSpec {

    void 'failure fixture exercises eventual polling and hook failure path'() {
        given:
        MetadataTckFixture fixture = getMetadataFixture()
        StorageDescriptor primary = fixture.primaryStorageDescriptor
        UploadRequest successRequest = UploadRequest.fromBytes(TEXT.bytes, 'images/polled.png', CONTENT_TYPE)
        successRequest.metadata = METADATA
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        UploadResponse<?> response = fixture.withTenant('tenant-alpha') {
            fixture.upload(primary, successRequest)
        }

        then:
        response.key == 'images/polled.png'

        when:
        conditions.eventually {
            assert fixture.withTenant('tenant-alpha') {
                fixture.metadataOperations.findObject(primary, 'images/polled.png')
            }.present
        }
        UploadRequest failingRequest = UploadRequest.fromBytes(TEXT.bytes, 'images/hook-failure.png', CONTENT_TYPE)
        failingRequest.metadata = METADATA
        fixture.withTenant('tenant-alpha') {
            fixture.uploadWithError(primary, failingRequest)
        }

        then:
        MetadataDispatchException e = thrown()
        e.operationContext.objectKey == 'images/hook-failure.png'
        fixture.lifecycleEvents*.phase == ['before', 'before', 'error']
        fixture.lifecycleEvents*.hookName == ['high-precedence', 'low-precedence', 'high-precedence']
        fixture.lifecycleEvents.last().throwable.is(e)
    }
}
