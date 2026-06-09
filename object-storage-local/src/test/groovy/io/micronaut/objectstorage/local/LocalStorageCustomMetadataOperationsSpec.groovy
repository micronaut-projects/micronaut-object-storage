package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest

import java.nio.file.Files
import java.nio.file.Path

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
}
