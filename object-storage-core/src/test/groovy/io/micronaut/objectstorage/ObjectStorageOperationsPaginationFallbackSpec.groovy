package io.micronaut.objectstorage

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification
import spock.lang.Subject

import java.util.Optional
import java.util.Set
import java.util.function.Consumer

class ObjectStorageOperationsPaginationFallbackSpec extends Specification {

    @Subject
    ObjectStorageOperations<Object, Object, Object> operations = new LegacyOnlyOperations()

    void "it lists first page from legacy keys using sorted filtered fallback"() {
        when:
        def response = operations.listObjects(new ListObjectsRequest(2, "animals/"))

        then:
        response.keys == ["animals/cat.txt", "animals/dog.txt"]
        response.continuationToken.get() == "animals/dog.txt"
    }

    void "it starts strictly after continuation token in sorted filtered keys"() {
        when:
        def response = operations.listObjects(new ListObjectsRequest(2, "animals/", "animals/dog.txt"))

        then:
        response.keys == ["animals/mammals/fox.txt"]
        response.continuationToken.empty
    }

    void "it starts from the first lexicographically greater key when the continuation token is unknown"() {
        when:
        def response = operations.listObjects(new ListObjectsRequest(2, "animals/", "animals/cow.txt"))

        then:
        response.keys == ["animals/dog.txt", "animals/mammals/fox.txt"]
        response.continuationToken.empty
    }

    void "it starts from first matching key when continuation token is absent"() {
        when:
        def response = operations.listObjects(new ListObjectsRequest(3, "plants/"))

        then:
        response.keys == ["plants/oak.txt"]
        response.continuationToken.empty
    }

    void "it falls back to full sorted legacy listing without prefix"() {
        when:
        def response = operations.listObjects(new ListObjectsRequest(3))

        then:
        response.keys == ["animals/cat.txt", "animals/dog.txt", "animals/mammals/fox.txt"]
        response.continuationToken.get() == "animals/mammals/fox.txt"
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

        @Override
        Set<String> listObjects() {
            ["plants/oak.txt", "animals/mammals/fox.txt", "animals/dog.txt", "animals/cat.txt"] as Set
        }
    }
}
