package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@Property(name = "spec.name", value = AbstractLocalStorageCustomMetadataSpec.SPEC_NAME)
@Property(name = "micronaut.object-storage.local.default.enabled", value = "true")
@MicronautTest
class LocalStorageCustomMetadataOperationsSpec extends AbstractLocalStorageCustomMetadataSpec {

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

    void 'local object storage delegates content etag to configured metadata operations'() {
        when:
        def firstResponse = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'etag/first.txt', 'text/plain'))
        def secondResponse = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'etag/second.txt', 'text/plain'))
        def changedResponse = operations.upload(UploadRequest.fromBytes('hello!'.bytes, 'etag/changed.txt', 'text/plain'))

        then:
        firstResponse.ETag == sha256ETag('hello')
        firstResponse.ETag == secondResponse.ETag
        firstResponse.ETag != changedResponse.ETag
        objectMetadataOperations.retrieve('etag/first.txt').get().etag == firstResponse.ETag
    }

    void 'local copy delegates object metadata to configured metadata operations'() {
        given:
        UploadRequest request = UploadRequest.fromBytes('hello'.bytes, 'source.txt', 'text/plain')
        request.metadata = [source: 'custom']
        def sourceResponse = operations.upload(request)

        when:
        operations.copy('source.txt', 'copy.txt')

        then:
        objectMetadataOperations.retrieve('copy.txt').get().metadata == [source: 'custom']
        objectMetadataOperations.retrieve('copy.txt').get().etag == sourceResponse.ETag
        operations.retrieve('copy.txt').get().metadata == [source: 'custom']
        operations.retrieve('copy.txt').get().inputStream.text == 'hello'
    }

    void 'local copy persists content etag when source metadata is missing'() {
        given:
        def sourceResponse = operations.upload(UploadRequest.fromBytes('source'.bytes, 'source-without-metadata.txt', 'text/plain'))
        UploadRequest destination = UploadRequest.fromBytes('destination'.bytes, 'destination.txt', 'text/plain')
        destination.metadata = [source: 'stale']
        operations.upload(destination)
        objectMetadataOperations.delete('source-without-metadata.txt')
        objectMetadataOperations.deletedKeys.clear()

        when:
        operations.copy('source-without-metadata.txt', 'destination.txt')

        then:
        objectMetadataOperations.deletedKeys.empty
        objectMetadataOperations.retrieve('destination.txt').get().metadata == [:]
        objectMetadataOperations.retrieve('destination.txt').get().etag == sourceResponse.ETag
        operations.retrieve('destination.txt').get().metadata == [:]
        operations.retrieve('destination.txt').get().inputStream.text == 'source'
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
            'spec.name': AbstractLocalStorageCustomMetadataSpec.SPEC_NAME,
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
    private static String sha256ETag(String text) {
        MessageDigest.getInstance('SHA-256')
            .digest(text.bytes)
            .collect { String.format('%02x', it & 0xff) }
            .join()
    }
}
