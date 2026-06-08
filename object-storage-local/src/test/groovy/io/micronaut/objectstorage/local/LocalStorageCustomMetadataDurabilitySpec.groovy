package io.micronaut.objectstorage.local

import io.micronaut.context.annotation.Property
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Property(name = "spec.name", value = AbstractLocalStorageCustomMetadataSpec.SPEC_NAME)
@Property(name = "micronaut.object-storage.local.default.enabled", value = "true")
@MicronautTest
class LocalStorageCustomMetadataDurabilitySpec extends AbstractLocalStorageCustomMetadataSpec {

    void 'local self copy is a no op'() {
        given:
        UploadRequest request = UploadRequest.fromBytes('self'.bytes, 'self-copy.txt', 'text/plain')
        request.metadata = [source: 'original']
        operations.upload(request)
        objectMetadataOperations.failSaves = true

        when:
        operations.copy('self-copy.txt', 'self-copy.txt')

        then:
        noExceptionThrown()
        text('self-copy.txt') == 'self'
        objectMetadataOperations.retrieve('self-copy.txt').get().metadata == [source: 'original']
    }

    void 'failed object metadata save removes a newly created object'() {
        given:
        objectMetadataOperations.failSaves = true

        when:
        operations.upload(UploadRequest.fromBytes('new'.bytes, 'new.txt', 'text/plain'))

        then:
        IllegalStateException e = thrown()
        e.message == 'metadata unavailable'
        !operations.exists('new.txt')
    }

    void 'failed object metadata save does not delete an existing object'() {
        given:
        operations.upload(UploadRequest.fromBytes('original'.bytes, 'existing.txt', 'text/plain'))
        objectMetadataOperations.failSaves = true

        when:
        operations.upload(UploadRequest.fromBytes('replacement'.bytes, 'existing.txt', 'text/plain'))

        then:
        IllegalStateException e = thrown()
        e.message == 'metadata unavailable'
        operations.exists('existing.txt')
        new String(operations.retrieve('existing.txt').get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == 'original'
    }

    void 'snapshot cleanup failure does not fail committed upload'() {
        given:
        String key = 'snapshot-cleanup-failure.txt'
        operations.upload(UploadRequest.fromBytes('original'.bytes, key, 'text/plain'))
        Path objectPath = operations.retrieve(key).get().nativeEntry
        Path snapshotDirectory = snapshotBucketDirectory(objectPath)
        objectMetadataOperations.saveCallback = { ObjectMetadataWrite ignored ->
            replaceSnapshotsWithNonEmptyDirectories(snapshotDirectory)
        }

        when:
        operations.upload(UploadRequest.fromBytes('replacement'.bytes, key, 'text/plain'))

        then:
        noExceptionThrown()
        text(key) == 'replacement'
        containsNonEmptyDirectory(snapshotDirectory)

        cleanup:
        objectMetadataOperations.saveCallback = null
        if (snapshotDirectory != null && Files.exists(snapshotDirectory)) {
            LocalStorageBucketOperations.deleteRecursively(snapshotDirectory)
        }
    }

    void 'upload uses the resolved request key consistently'() {
        given:
        ChangingKeyUploadRequest request = new ChangingKeyUploadRequest('content'.bytes, 'consistent.txt', 'wrong.txt')

        when:
        def response = operations.upload(request)

        then:
        response.key == 'consistent.txt'
        request.keyCalls == 1
        operations.exists('consistent.txt')
        !operations.exists('wrong.txt')
        objectMetadataOperations.retrieve('consistent.txt').present
    }

    void 'failed object metadata save cannot restore over concurrent same key upload'() {
        given:
        String key = 'race.txt'
        operations.upload(UploadRequest.fromBytes('original'.bytes, key, 'text/plain'))
        CountDownLatch failingSaveStarted = new CountDownLatch(1)
        CountDownLatch allowFailingSave = new CountDownLatch(1)
        CountDownLatch successfulKeyRequested = new CountDownLatch(1)
        CountDownLatch successfulInputRequested = new CountDownLatch(1)
        objectMetadataOperations.failNextSave(key, failingSaveStarted, allowFailingSave)
        ExecutorService executor = Executors.newFixedThreadPool(2)
        Future<?> failingUpload = null
        Future<?> successfulUpload = null

        when:
        failingUpload = executor.submit({
            operations.upload(UploadRequest.fromBytes('failed'.bytes, key, 'text/plain'))
        } as Callable)

        then:
        failingSaveStarted.await(5, TimeUnit.SECONDS) == true

        when:
        successfulUpload = executor.submit({
            operations.upload(new InputTrackedUploadRequest('winner'.bytes, key, successfulKeyRequested, successfulInputRequested))
        } as Callable)

        then:
        successfulKeyRequested.await(5, TimeUnit.SECONDS) == true
        successfulInputRequested.await(1, TimeUnit.SECONDS) == false

        when:
        allowFailingSave.countDown()
        failingUpload.get(5, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == 'metadata unavailable'

        when:
        successfulUpload.get(5, TimeUnit.SECONDS)

        then:
        successfulInputRequested.await(5, TimeUnit.SECONDS) == true
        operations.exists(key)
        text(key) == 'winner'

        cleanup:
        allowFailingSave?.countDown()
        executor?.shutdownNow()
    }

    void 'failed object metadata delete cannot restore over concurrent same key upload'() {
        given:
        String key = 'delete-race.txt'
        operations.upload(UploadRequest.fromBytes('original'.bytes, key, 'text/plain'))
        CountDownLatch failingDeleteStarted = new CountDownLatch(1)
        CountDownLatch allowFailingDelete = new CountDownLatch(1)
        CountDownLatch successfulKeyRequested = new CountDownLatch(1)
        CountDownLatch successfulInputRequested = new CountDownLatch(1)
        objectMetadataOperations.failNextDelete(key, failingDeleteStarted, allowFailingDelete)
        ExecutorService executor = Executors.newFixedThreadPool(2)
        Future<?> failingDelete = null
        Future<?> successfulUpload = null

        when:
        failingDelete = executor.submit({
            operations.delete(key)
        } as Callable)

        then:
        failingDeleteStarted.await(5, TimeUnit.SECONDS) == true

        when:
        successfulUpload = executor.submit({
            operations.upload(new InputTrackedUploadRequest('winner'.bytes, key, successfulKeyRequested, successfulInputRequested))
        } as Callable)

        then:
        successfulKeyRequested.await(5, TimeUnit.SECONDS) == true
        successfulInputRequested.await(1, TimeUnit.SECONDS) == false

        when:
        allowFailingDelete.countDown()
        failingDelete.get(5, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == 'metadata unavailable'

        when:
        successfulUpload.get(5, TimeUnit.SECONDS)

        then:
        successfulInputRequested.await(5, TimeUnit.SECONDS) == true
        operations.exists(key)
        text(key) == 'winner'

        cleanup:
        allowFailingDelete?.countDown()
        executor?.shutdownNow()
    }

    void 'failed copy metadata save removes a newly created destination object'() {
        given:
        operations.upload(UploadRequest.fromBytes('source'.bytes, 'source.txt', 'text/plain'))
        objectMetadataOperations.failSaves = true

        when:
        operations.copy('source.txt', 'failed-copy.txt')

        then:
        IllegalStateException e = thrown()
        e.message == 'metadata unavailable'
        !operations.exists('failed-copy.txt')
    }

    void 'failed copy metadata save preserves an existing destination object'() {
        given:
        operations.upload(UploadRequest.fromBytes('source'.bytes, 'source.txt', 'text/plain'))
        operations.upload(UploadRequest.fromBytes('destination'.bytes, 'destination.txt', 'text/plain'))
        objectMetadataOperations.failSaves = true

        when:
        operations.copy('source.txt', 'destination.txt')

        then:
        IllegalStateException e = thrown()
        e.message == 'metadata unavailable'
        operations.exists('destination.txt')
        def entry = operations.retrieve('destination.txt').get()
        new String(entry.inputStream.readAllBytes(), StandardCharsets.UTF_8) == 'destination'
        !Files.exists(snapshotBucketDirectory(entry.nativeEntry))
    }

    void 'failed copy metadata save cannot restore over concurrent destination upload'() {
        given:
        String sourceKey = 'copy-source-race.txt'
        String destinationKey = 'copy-destination-race.txt'
        operations.upload(UploadRequest.fromBytes('source'.bytes, sourceKey, 'text/plain'))
        operations.upload(UploadRequest.fromBytes('destination'.bytes, destinationKey, 'text/plain'))
        CountDownLatch failingSaveStarted = new CountDownLatch(1)
        CountDownLatch allowFailingSave = new CountDownLatch(1)
        CountDownLatch successfulKeyRequested = new CountDownLatch(1)
        CountDownLatch successfulInputRequested = new CountDownLatch(1)
        objectMetadataOperations.failNextSave(destinationKey, failingSaveStarted, allowFailingSave)
        ExecutorService executor = Executors.newFixedThreadPool(2)
        Future<?> failingCopy = null
        Future<?> successfulUpload = null

        when:
        failingCopy = executor.submit({
            operations.copy(sourceKey, destinationKey)
        } as Callable)

        then:
        failingSaveStarted.await(5, TimeUnit.SECONDS) == true

        when:
        successfulUpload = executor.submit({
            operations.upload(new InputTrackedUploadRequest('winner'.bytes, destinationKey, successfulKeyRequested, successfulInputRequested))
        } as Callable)

        then:
        successfulKeyRequested.await(5, TimeUnit.SECONDS) == true
        successfulInputRequested.await(1, TimeUnit.SECONDS) == false

        when:
        allowFailingSave.countDown()
        failingCopy.get(5, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == 'metadata unavailable'

        when:
        successfulUpload.get(5, TimeUnit.SECONDS)

        then:
        successfulInputRequested.await(5, TimeUnit.SECONDS) == true
        operations.exists(destinationKey)
        text(destinationKey) == 'winner'

        cleanup:
        allowFailingSave?.countDown()
        executor?.shutdownNow()
    }

    void 'failed object metadata delete preserves the stored object'() {
        given:
        operations.upload(UploadRequest.fromBytes('content'.bytes, 'delete-failure.txt', 'text/plain'))
        objectMetadataOperations.failDeletes = true

        when:
        operations.delete('delete-failure.txt')

        then:
        IllegalStateException e = thrown()
        e.message == 'metadata unavailable'
        operations.exists('delete-failure.txt')
        def entry = operations.retrieve('delete-failure.txt').get()
        new String(entry.inputStream.readAllBytes(), StandardCharsets.UTF_8) == 'content'
        !Files.exists(snapshotBucketDirectory(entry.nativeEntry))
    }

    private static void replaceSnapshotsWithNonEmptyDirectories(Path snapshotDirectory) {
        List<Path> snapshots = Files.list(snapshotDirectory).withCloseable { stream ->
            stream.toList()
        }
        snapshots.each { snapshot ->
            Files.delete(snapshot)
            Files.createDirectory(snapshot)
            Files.writeString(snapshot.resolve('still-here'), 'snapshot')
        }
    }

    private static boolean containsNonEmptyDirectory(Path directory) {
        Files.list(directory).withCloseable { stream ->
            stream.anyMatch { path -> Files.isDirectory(path) && Files.exists(path.resolve('still-here')) }
        }
    }

    private static Path snapshotBucketDirectory(Path objectPath) {
        Path bucketPath = objectPath.parent
        bucketPath.parent
            .resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.SNAPSHOT_DIRECTORY)
            .resolve(bucketPath.fileName.toString())
    }
}
