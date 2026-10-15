package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.metadata.BucketMetadataEntry
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class LocalStorageNoOpMetadataSpec extends Specification {

    void 'no-op metadata beans accept uploads and support object and bucket lifecycles without sidecars'() {
        given:
        Path root = Files.createTempDirectory('local-no-op-metadata')
        ApplicationContext context = ApplicationContext.run([
            'spec.name': 'LocalStorageNoOpMetadataSpec',
            'micronaut.object-storage.local.default.path': root.resolve('default').toString()
        ])
        LocalStorageOperations operations = context.getBean(LocalStorageOperations)
        LocalStorageMultipartOperations multipart = context.getBean(LocalStorageMultipartOperations)
        LocalStorageBucketOperations buckets = context.getBean(LocalStorageBucketOperations)
        def request = UploadRequest.fromBytes('content'.bytes, 'object.txt')
        request.metadata = [owner: 'application']

        when:
        def response = operations.upload(request)
        operations.copy('object.txt', 'copy.txt')
        def created = multipart.createMultipartUpload(new CreateMultipartUploadRequest('multipart.txt', 'text/plain', [owner: 'application']))
        def part = multipart.uploadPart(new UploadPartRequest(created.upload, 1, UploadRequest.fromBytes('content'.bytes, 'multipart.txt')))
        def completed = multipart.completeMultipartUpload(new CompleteMultipartUploadRequest(created.upload, [part.part]))

        then:
        response.ETag == MessageDigest.getInstance('SHA-256').digest('content'.bytes).encodeHex().toString()
        completed.ETag == response.ETag
        operations.retrieve('object.txt').get().metadata.isEmpty()
        operations.retrieve('copy.txt').get().inputStream.text == 'content'
        operations.retrieve('multipart.txt').get().inputStream.text == 'content'
        !Files.exists(root.resolve('.mn-storage/metadata'))
        !Files.exists(created.nativeResponse.path)

        when:
        operations.delete('object.txt')
        operations.delete('object.txt')
        buckets.create('other')
        context.getBean(BucketMetadataOperations).save(new BucketMetadataWrite('other', [owner: 'application']))
        buckets.delete('other')

        then:
        !operations.exists('object.txt')
        !buckets.retrieve('other').present
        !Files.exists(root.resolve('.mn-storage/metadata'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'no-op metadata leaves existing sidecars untouched'() {
        given:
        Path root = Files.createTempDirectory('local-no-op-existing')
        Path sidecar = root.resolve('.mn-storage/metadata/objects/default/object.txt')
        Files.createDirectories(sidecar.parent)
        Files.writeString(sidecar, 'owner=old')
        ApplicationContext context = ApplicationContext.run([
            'spec.name': 'LocalStorageNoOpMetadataSpec',
            'micronaut.object-storage.local.default.path': root.resolve('default').toString()
        ])
        LocalStorageOperations operations = context.getBean(LocalStorageOperations)

        when:
        operations.upload(UploadRequest.fromBytes('content'.bytes, 'object.txt'))

        then:
        operations.retrieve('object.txt').get().metadata.isEmpty()

        when:
        operations.delete('object.txt')

        then:
        Files.readString(sidecar) == 'owner=old'

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    @Factory
    @Requires(property = 'spec.name', value = 'LocalStorageNoOpMetadataSpec')
    static class NoOpMetadataFactory {
        @Singleton
        @Primary
        @Named('default')
        ObjectMetadataOperations<Path> objectMetadataOperations() {
            new ObjectMetadataOperations<Path>() {
                Optional<ObjectMetadataEntry<Path>> retrieve(String key) { Optional.empty() }
                void save(ObjectMetadataWrite write) { }
                void delete(String key) { }
            }
        }

        @Singleton
        @Primary
        @Named('default')
        BucketMetadataOperations<Path> bucketMetadataOperations() {
            new BucketMetadataOperations<Path>() {
                Optional<BucketMetadataEntry<Path>> retrieve(String name) { Optional.empty() }
                void save(BucketMetadataWrite write) { }
                void delete(String name) { }
            }
        }
    }
}
