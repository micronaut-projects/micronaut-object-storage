package io.micronaut.objectstorage.internal

import io.micronaut.objectstorage.metadata.ObjectMetadataEntry
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultReactiveObjectMetadataOperationsSpec extends Specification {

    private final ExecutorService executor = Executors.newSingleThreadExecutor()

    void cleanup() {
        executor.shutdownNow()
    }

    void "reactive adapter delegates save and delete as completion-only publishers"() {
        given:
        ObjectMetadataOperations<String> delegate = Mock()
        DefaultReactiveObjectMetadataOperations<String> operations =
            new DefaultReactiveObjectMetadataOperations<>(delegate, executor)
        ObjectMetadataWrite write = new ObjectMetadataWrite("reports/2026.txt", [team: "core"], [owner: "cliponaut"], "text/plain", 42L, null, null)

        when:
        List<Void> saveSignals = Flux.from(operations.save(write)).collectList().block()
        List<Void> deleteSignals = Flux.from(operations.delete("reports/2026.txt")).collectList().block()

        then:
        saveSignals.empty
        deleteSignals.empty
        1 * delegate.save(write)
        1 * delegate.delete("reports/2026.txt")
    }

    void "reactive adapter emits retrieve and exists results"() {
        given:
        ObjectMetadataOperations<String> delegate = Mock()
        DefaultReactiveObjectMetadataOperations<String> operations =
            new DefaultReactiveObjectMetadataOperations<>(delegate, executor)
        ObjectMetadataEntry<String> entry =
            new ObjectMetadataEntry<>("reports/2026.txt", [team: "core"], [owner: "cliponaut"], "text/plain", 42L, "etag", null, "native")

        when:
        Optional<ObjectMetadataEntry<String>> retrieveResult = Mono.from(operations.retrieve("reports/2026.txt")).block()
        Boolean exists = Mono.from(operations.exists("reports/2026.txt")).block()

        then:
        retrieveResult.present
        retrieveResult.get() == entry
        exists
        1 * delegate.retrieve("reports/2026.txt") >> Optional.of(entry)
        1 * delegate.exists("reports/2026.txt") >> true
    }
}
