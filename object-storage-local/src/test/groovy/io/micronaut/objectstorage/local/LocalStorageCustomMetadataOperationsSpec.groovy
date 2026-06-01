package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MetadataOperationsFactory {

        @Singleton
        @Named("default")
        CustomObjectMetadataOperations objectMetadataOperations() {
            new CustomObjectMetadataOperations()
        }

        @Singleton
        @Named("default")
        CustomBucketMetadataOperations bucketMetadataOperations() {
            new CustomBucketMetadataOperations()
        }
    }

    static class CustomObjectMetadataOperations implements ObjectMetadataOperations<Path> {
        final Map<String, ObjectMetadataWrite> writes = new ConcurrentHashMap<>()
        final List<String> deletedKeys = Collections.synchronizedList([])
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
            if (failSaves) {
                throw new IllegalStateException('metadata unavailable')
            }
            writes.put(write.key(), write)
        }

        @Override
        void delete(String key) {
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
