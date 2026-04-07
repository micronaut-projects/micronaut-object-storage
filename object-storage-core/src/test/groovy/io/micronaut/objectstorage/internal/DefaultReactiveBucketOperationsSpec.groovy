package io.micronaut.objectstorage.internal

import io.micronaut.objectstorage.bucket.BucketEntry
import io.micronaut.objectstorage.bucket.BucketOperations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultReactiveBucketOperationsSpec extends Specification {

    private final ExecutorService executor = Executors.newSingleThreadExecutor()

    void cleanup() {
        executor.shutdownNow()
    }

    void "reactive adapter delegates create and delete as completion-only publishers"() {
        given:
        BucketOperations<String> delegate = Mock()
        DefaultReactiveBucketOperations<String> operations = new DefaultReactiveBucketOperations<>(delegate, executor)

        when:
        List<Void> createSignals = Flux.from(operations.create("reports")).collectList().block()
        List<Void> deleteSignals = Flux.from(operations.delete("reports")).collectList().block()

        then:
        createSignals.empty
        deleteSignals.empty
        1 * delegate.create("reports")
        1 * delegate.delete("reports")
    }

    void "reactive adapter emits retrieve and exists results"() {
        given:
        BucketOperations<String> delegate = Mock()
        DefaultReactiveBucketOperations<String> operations = new DefaultReactiveBucketOperations<>(delegate, executor)
        BucketEntry<String> entry = new BucketEntry<>("reports", "native")

        when:
        Optional<BucketEntry<String>> retrieveResult = Mono.from(operations.retrieve("reports")).block()
        Boolean exists = Mono.from(operations.exists("reports")).block()

        then:
        retrieveResult.present
        retrieveResult.get() == entry
        exists
        1 * delegate.retrieve("reports") >> Optional.of(entry)
        1 * delegate.exists("reports") >> true
    }
}
