package io.micronaut.objectstorage.internal

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.ListObjectsResponse
import io.micronaut.objectstorage.response.UploadResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultReactiveObjectStorageOperationsSpec extends Specification {

    private final ExecutorService executor = Executors.newSingleThreadExecutor()

    void cleanup() {
        executor.shutdownNow()
    }

    void "reactive adapter delegates upload style operations on the blocking executor"() {
        given:
        ObjectStorageOperations<String, String, Boolean> delegate = Mock()
        DefaultReactiveObjectStorageOperations<String, String, Boolean> operations =
            new DefaultReactiveObjectStorageOperations<>(delegate, executor)
        UploadRequest request = UploadRequest.fromBytes("test".bytes, "dir/file.txt")
        UploadResponse<String> response = UploadResponse.of(request.key, "etag", "native")

        when:
        UploadResponse<String> result = Mono.from(operations.upload(request)).block()

        then:
        result == response
        1 * delegate.upload(request) >> response
    }

    void "reactive adapter preserves optional retrieve semantics and completion-only copy"() {
        given:
        ObjectStorageOperations<String, String, Boolean> delegate = Mock()
        DefaultReactiveObjectStorageOperations<String, String, Boolean> operations =
            new DefaultReactiveObjectStorageOperations<>(delegate, executor)
        ObjectStorageEntry<?> entry = Mock()

        when:
        Optional<ObjectStorageEntry<?>> retrieveResult = Mono.from(operations.retrieve("dir/file.txt")).block()
        List<Void> copySignals = Flux.from(operations.copy("dir/file.txt", "copy.txt")).collectList().block()

        then:
        retrieveResult.present
        retrieveResult.get() == entry
        copySignals.empty
        1 * delegate.retrieve("dir/file.txt") >> Optional.of(entry)
        1 * delegate.copy("dir/file.txt", "copy.txt")
    }

    void "reactive adapter emits list and delete results"() {
        given:
        ObjectStorageOperations<String, String, Boolean> delegate = Mock()
        DefaultReactiveObjectStorageOperations<String, String, Boolean> operations =
            new DefaultReactiveObjectStorageOperations<>(delegate, executor)
        ListObjectsRequest request = new ListObjectsRequest(10, "animals/")
        ListObjectsResponse listResponse = new ListObjectsResponse(["animals/cat.txt"], null)

        when:
        Boolean exists = Mono.from(operations.exists("animals/cat.txt")).block()
        Set<String> keys = Mono.from(operations.listObjects()).block()
        ListObjectsResponse page = Mono.from(operations.listObjects(request)).block()
        Boolean deleteResult = Mono.from(operations.delete("animals/cat.txt")).block()

        then:
        exists
        keys == ["animals/cat.txt"] as Set
        page == listResponse
        deleteResult
        1 * delegate.exists("animals/cat.txt") >> true
        1 * delegate.listObjects() >> (["animals/cat.txt"] as Set)
        1 * delegate.listObjects(request) >> listResponse
        1 * delegate.delete("animals/cat.txt") >> true
    }
}
