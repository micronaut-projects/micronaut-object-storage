package io.micronaut.objectstorage.local

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.metadata.BucketMetadataEntry
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractLocalStorageCustomMetadataSpec extends Specification {

    static final String SPEC_NAME = 'LocalStorageCustomMetadataSpec'

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

    protected String text(String key) {
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
        Closure<?> saveCallback
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
            saveCallback?.call(write)
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
            saveCallback = null
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

    protected static final class ChangingKeyUploadRequest implements UploadRequest {

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

    protected static final class InputTrackedUploadRequest implements UploadRequest {

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
