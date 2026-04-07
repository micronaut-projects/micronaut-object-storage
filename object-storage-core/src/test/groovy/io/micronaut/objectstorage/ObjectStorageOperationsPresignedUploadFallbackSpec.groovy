package io.micronaut.objectstorage

import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.util.Optional
import java.util.function.Consumer

class ObjectStorageOperationsPresignedUploadFallbackSpec extends Specification {

    @Subject
    ObjectStorageOperations<Object, Object, Object> operations = new LegacyOnlyOperations()

    void "it returns empty for providers that do not override the presigned upload API"() {
        expect:
        operations.createPresignedUpload(new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5))).empty
    }

    private static final class LegacyOnlyOperations implements ObjectStorageOperations<Object, Object, Object> {

        @Override
        UploadResponse<Object> upload(UploadRequest request) {
            throw new UnsupportedOperationException()
        }

        @Override
        UploadResponse<Object> upload(UploadRequest request, Consumer<Object> requestConsumer) {
            throw new UnsupportedOperationException()
        }

        @Override
        Optional<ObjectStorageEntry<?>> retrieve(String key) {
            throw new UnsupportedOperationException()
        }

        @Override
        Object delete(String key) {
            throw new UnsupportedOperationException()
        }
    }
}
