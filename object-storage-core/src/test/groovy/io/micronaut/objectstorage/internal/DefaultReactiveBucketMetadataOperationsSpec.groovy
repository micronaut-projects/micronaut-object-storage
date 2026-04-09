package io.micronaut.objectstorage.internal

import io.micronaut.objectstorage.metadata.BucketMetadataEntry
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultReactiveBucketMetadataOperationsSpec extends Specification {

    private final ExecutorService executor = Executors.newSingleThreadExecutor()

    void cleanup() {
        executor.shutdownNow()
    }

    void "reactive adapter delegates save and delete as completion-only publishers"() {
        given:
        BucketMetadataOperations<String> delegate = Mock()
        DefaultReactiveBucketMetadataOperations<String> operations =
            new DefaultReactiveBucketMetadataOperations<>(delegate, executor)
        BucketMetadataWrite write = new BucketMetadataWrite("reports", [region: "test"], [owner: "cliponaut"])

        when:
        List<Void> saveSignals = Flux.from(operations.save(write)).collectList().block()
        List<Void> deleteSignals = Flux.from(operations.delete("reports")).collectList().block()

        then:
        saveSignals.empty
        deleteSignals.empty
        1 * delegate.save(write)
        1 * delegate.delete("reports")
    }

    void "reactive adapter emits retrieve and exists results"() {
        given:
        BucketMetadataOperations<String> delegate = Mock()
        DefaultReactiveBucketMetadataOperations<String> operations =
            new DefaultReactiveBucketMetadataOperations<>(delegate, executor)
        BucketMetadataEntry<String> entry = new BucketMetadataEntry<>("reports", [region: "test"], [owner: "cliponaut"], "native")

        when:
        Optional<BucketMetadataEntry<String>> retrieveResult = Mono.from(operations.retrieve("reports")).block()
        Boolean exists = Mono.from(operations.exists("reports")).block()

        then:
        retrieveResult.present
        retrieveResult.get() == entry
        exists
        1 * delegate.retrieve("reports") >> Optional.of(entry)
        1 * delegate.exists("reports") >> true
    }
}
