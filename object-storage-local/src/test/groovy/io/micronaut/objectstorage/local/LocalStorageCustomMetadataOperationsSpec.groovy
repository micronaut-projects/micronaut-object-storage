package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.metadata.BucketMetadataEntry
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Property(name = "spec.name", value = LocalStorageCustomMetadataOperationsSpec.SPEC_NAME)
@Property(name = "micronaut.object-storage.local.default.enabled", value = "true")
@MicronautTest
class LocalStorageCustomMetadataOperationsSpec extends Specification {

    static final String SPEC_NAME = 'LocalStorageCustomMetadataOperationsSpec'

    @Inject
    LocalStorageOperations operations

    @Inject
    LocalStorageBucketOperations bucketOperations

    @Inject
    @Named("default")
    CustomObjectMetadataOperations objectMetadataOperations

    @Inject
    @Named("default")
    CustomBucketMetadataOperations bucketMetadataOperations

    void cleanup() {
        objectMetadataOperations.clearFailures()
        operations.listObjects().each { operations.delete(it) }
        objectMetadataOperations.reset()
        bucketMetadataOperations.reset()
    }

    void 'local object storage delegates object metadata to configured metadata operations'() {
        given:
        UploadRequest request = UploadRequest.fromBytes('hello'.bytes, 'nested/object.txt', 'text/plain')
        request.metadata = [source: 'custom']

        when:
        operations.upload(request)

        then:
        objectMetadataOperations.retrieve('nested/object.txt').get().metadata == [source: 'custom']
        operations.retrieve('nested/object.txt').get().metadata == [source: 'custom']

        when:
        operations.delete('nested/object.txt')

        then:
        objectMetadataOperations.deletedKeys == ['nested/object.txt']
    }

    void 'local copy delegates object metadata to configured metadata operations'() {
        given:
        UploadRequest request = UploadRequest.fromBytes('hello'.bytes, 'source.txt', 'text/plain')
        request.metadata = [source: 'custom']
        operations.upload(request)

        when:
        operations.copy('source.txt', 'copy.txt')

        then:
        objectMetadataOperations.retrieve('copy.txt').get().metadata == [source: 'custom']
        operations.retrieve('copy.txt').get().metadata == [source: 'custom']
        operations.retrieve('copy.txt').get().inputStream.text == 'hello'
    }

    void 'local copy deletes destination metadata when source metadata is missing'() {
        given:
        operations.upload(UploadRequest.fromBytes('source'.bytes, 'source-without-metadata.txt', 'text/plain'))
        UploadRequest destination = UploadRequest.fromBytes('destination'.bytes, 'destination.txt', 'text/plain')
        destination.metadata = [source: 'stale']
        operations.upload(destination)
        objectMetadataOperations.delete('source-without-metadata.txt')
        objectMetadataOperations.deletedKeys.clear()

        when:
        operations.copy('source-without-metadata.txt', 'destination.txt')

        then:
        objectMetadataOperations.deletedKeys == ['destination.txt']
        !objectMetadataOperations.retrieve('destination.txt').present
        operations.retrieve('destination.txt').get().metadata == [:]
        operations.retrieve('destination.txt').get().inputStream.text == 'source'
    }

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
        !Files.exists(entry.nativeEntry.parent.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))
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
        !Files.exists(entry.nativeEntry.parent.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.SNAPSHOT_DIRECTORY))
    }

    void 'local bucket storage delegates bucket metadata to configured metadata operations'() {
        given:
        String bucketName = 'custom-bucket'
        bucketOperations.create(bucketName)
        bucketMetadataOperations.save(new BucketMetadataWrite(bucketName, [source: 'custom']))

        expect:
        bucketMetadataOperations.retrieve(bucketName).get().metadata == [source: 'custom']

        when:
        bucketOperations.delete(bucketName)

        then:
        bucketMetadataOperations.deletedNames == [bucketName]
        !bucketMetadataOperations.retrieve(bucketName).present
    }

    void 'custom metadata operations do not create sidecar metadata directories on context startup'() {
        given:
        Path rootDirectory = Files.createTempDirectory('LocalStorageCustomMetadataOperationsSpec')
        Path bucketPath = rootDirectory.resolve('default')
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'spec.name': SPEC_NAME,
            'micronaut.object-storage.local.default.path': bucketPath.toString()
        ])
        context.getBean(LocalStorageOperations)
        context.getBean(LocalStorageBucketOperations)

        then:
        !Files.exists(bucketPath.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))
        !Files.exists(rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))

        cleanup:
        context?.close()
        if (Files.exists(rootDirectory)) {
            LocalStorageBucketOperations.deleteRecursively(rootDirectory)
        }
    }

    private String text(String key) {
        new String(operations.retrieve(key).get().inputStream.readAllBytes(), StandardCharsets.UTF_8)
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MetadataOperationsFactory {

        @Singleton
        @Named("default")
        @Primary
        CustomObjectMetadataOperations objectMetadataOperations() {
            new CustomObjectMetadataOperations()
        }

        @Singleton
        @Named("default")
        @Primary
        CustomBucketMetadataOperations bucketMetadataOperations() {
            new CustomBucketMetadataOperations()
        }
    }

    static class CustomObjectMetadataOperations implements ObjectMetadataOperations<Path> {
        final Map<String, ObjectMetadataWrite> writes = new ConcurrentHashMap<>()
        final List<String> deletedKeys = Collections.synchronizedList([])
        final Set<String> failNextSaveKeys = ConcurrentHashMap.newKeySet()
        final Set<String> failNextDeleteKeys = ConcurrentHashMap.newKeySet()
        CountDownLatch saveFailureStarted
        CountDownLatch allowSaveFailure
        CountDownLatch deleteFailureStarted
        CountDownLatch allowDeleteFailure
        boolean failSaves
        boolean failDeletes

        @Override
        Optional<ObjectMetadataEntry<Path>> retrieve(String key) {
            ObjectMetadataWrite write = writes.get(key)
            if (write == null) {
                return Optional.empty()
            }
            return Optional.of(new ObjectMetadataEntry<>(
                key,
                write.metadata(),
                write.attributes(),
                write.contentType(),
                write.contentLength(),
                write.etag(),
                write.lastModified(),
                Path.of('metadata', key)
            ))
        }

        @Override
        void save(ObjectMetadataWrite write) {
            if (failNextSaveKeys.remove(write.key())) {
                saveFailureStarted?.countDown()
                await(allowSaveFailure)
                throw new IllegalStateException('metadata unavailable')
            }
            if (failSaves) {
                throw new IllegalStateException('metadata unavailable')
            }
            writes.put(write.key(), write)
        }

        @Override
        void delete(String key) {
            if (failNextDeleteKeys.remove(key)) {
                deleteFailureStarted?.countDown()
                await(allowDeleteFailure)
                throw new IllegalStateException('metadata unavailable')
            }
            if (failDeletes) {
                throw new IllegalStateException('metadata unavailable')
            }
            deletedKeys.add(key)
            writes.remove(key)
        }

        void reset() {
            clearFailures()
            writes.clear()
            deletedKeys.clear()
        }

        void clearFailures() {
            failSaves = false
            failDeletes = false
            failNextSaveKeys.clear()
            failNextDeleteKeys.clear()
            saveFailureStarted = null
            allowSaveFailure = null
            deleteFailureStarted = null
            allowDeleteFailure = null
        }

        void failNextSave(String key, CountDownLatch saveFailureStarted, CountDownLatch allowSaveFailure) {
            failNextSaveKeys.add(key)
            this.saveFailureStarted = saveFailureStarted
            this.allowSaveFailure = allowSaveFailure
        }

        void failNextDelete(String key, CountDownLatch deleteFailureStarted, CountDownLatch allowDeleteFailure) {
            failNextDeleteKeys.add(key)
            this.deleteFailureStarted = deleteFailureStarted
            this.allowDeleteFailure = allowDeleteFailure
        }

        private static void await(CountDownLatch latch) {
            if (latch != null && !latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException('timed out waiting for metadata failure release')
            }
        }
    }

    private static final class ChangingKeyUploadRequest implements UploadRequest {

        private final byte[] bytes
        private final String firstKey
        private final String secondKey
        int keyCalls

        ChangingKeyUploadRequest(byte[] bytes, String firstKey, String secondKey) {
            this.bytes = bytes
            this.firstKey = firstKey
            this.secondKey = secondKey
        }

        @Override
        Optional<String> getContentType() {
            Optional.of('text/plain')
        }

        @Override
        String getKey() {
            keyCalls++
            keyCalls == 1 ? firstKey : secondKey
        }

        @Override
        Optional<Long> getContentSize() {
            Optional.of((long) bytes.length)
        }

        @Override
        InputStream getInputStream() {
            new ByteArrayInputStream(bytes)
        }
    }

    private static final class InputTrackedUploadRequest implements UploadRequest {

        private final byte[] bytes
        private final String key
        private final CountDownLatch keyRequested
        private final CountDownLatch inputRequested

        InputTrackedUploadRequest(byte[] bytes,
                                  String key,
                                  CountDownLatch keyRequested,
                                  CountDownLatch inputRequested) {
            this.bytes = bytes
            this.key = key
            this.keyRequested = keyRequested
            this.inputRequested = inputRequested
        }

        @Override
        Optional<String> getContentType() {
            Optional.of('text/plain')
        }

        @Override
        String getKey() {
            keyRequested.countDown()
            key
        }

        @Override
        Optional<Long> getContentSize() {
            Optional.of((long) bytes.length)
        }

        @Override
        InputStream getInputStream() {
            inputRequested.countDown()
            new ByteArrayInputStream(bytes)
        }
    }

    static class CustomBucketMetadataOperations implements BucketMetadataOperations<Path> {
        final Map<String, BucketMetadataWrite> writes = new ConcurrentHashMap<>()
        final List<String> deletedNames = Collections.synchronizedList([])

        @Override
        Optional<BucketMetadataEntry<Path>> retrieve(String name) {
            BucketMetadataWrite write = writes.get(name)
            if (write == null) {
                return Optional.empty()
            }
            return Optional.of(new BucketMetadataEntry<>(
                name,
                write.metadata(),
                write.attributes(),
                Path.of('metadata', name)
            ))
        }

        @Override
        void save(BucketMetadataWrite write) {
            writes.put(write.name(), write)
        }

        @Override
        void delete(String name) {
            deletedNames.add(name)
            writes.remove(name)
        }

        void reset() {
            writes.clear()
            deletedNames.clear()
        }
    }
}
