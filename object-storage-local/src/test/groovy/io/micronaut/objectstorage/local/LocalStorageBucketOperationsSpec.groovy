package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest

import java.nio.file.Files
import java.nio.file.Path

class LocalStorageBucketOperationsSpec extends BucketOperationsSpecification {

    private Path rootDirectory
    private Path defaultBucketPath
    private ApplicationContext ctx

    void setup() {
        rootDirectory = Files.createTempDirectory('LocalStorageBucketOperationsSpec')
        defaultBucketPath = rootDirectory.resolve('default')
        ctx = ApplicationContext.run([
            'micronaut.object-storage.local.default.path': defaultBucketPath.toString()
        ])
    }

    void cleanup() {
        ctx?.close()
        if (rootDirectory != null && Files.exists(rootDirectory)) {
            LocalStorageBucketOperations.deleteRecursively(rootDirectory)
        }
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        ctx.getBean(LocalStorageBucketOperations)
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        ctx.getBean(LocalStorageOperations)
    }

    @Override
    String newBucketName() {
        "bucket-${System.nanoTime()}"
    }

    void 'it rejects bucket names with path traversal'() {
        when:
        getBucketOperations().create('..')

        then:
        thrown IllegalArgumentException

        when:
        getBucketOperations().create('foo/bar')

        then:
        thrown IllegalArgumentException
    }

    void 'it rejects reserved bucket names'() {
        when:
        getBucketOperations().create(name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Bucket name uses the reserved ${reservedNamespace} namespace: ${name}"

        where:
        name          | reservedNamespace
        '.mn-storage' | '.mn-storage'
        '.Mn-Storage' | '.mn-storage'
        '.metadata'   | '.metadata'
        '.Metadata'   | '.metadata'
    }

    void 'failed bucket delete preserves metadata sidecars'() {
        given:
        Path bucket = rootDirectory.resolve('stuck')
        Files.writeString(bucket, 'not-a-directory')
        Path metadataFile = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve('buckets')
            .resolve('stuck')
        Files.createDirectories(metadataFile.parent)
        Files.writeString(metadataFile, 'owner=cliponaut')

        when:
        getBucketOperations().delete('stuck')

        then:
        thrown IllegalStateException
        Files.exists(metadataFile)
    }

    void 'bucket delete removes provider managed object metadata multipart state and snapshots'() {
        given:
        Path objectMetadataDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve(LocalStorageOperations.OBJECTS_DIRECTORY)
            .resolve('default')
        Path multipartDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.MULTIPART_DIRECTORY)
            .resolve('default')
        Path snapshotDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.SNAPSHOT_DIRECTORY)
            .resolve('default')
        getObjectStorage().upload(UploadRequest.fromBytes('hello'.bytes, 'delete-me.txt', 'text/plain'))
        def multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        def multipartUpload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest('multipart/delete-me.txt', 'text/plain')).upload
        multipartOperations.uploadPart(new UploadPartRequest(multipartUpload, 1, UploadRequest.fromBytes('part'.bytes, 'multipart/delete-me.txt', 'text/plain')))
        Files.createDirectories(snapshotDirectory)
        Files.writeString(snapshotDirectory.resolve('temporary'), 'snapshot')

        expect:
        Files.exists(objectMetadataDirectory.resolve('delete-me.txt'))
        Files.exists(multipartDirectory.resolve(multipartUpload.uploadId))
        Files.exists(snapshotDirectory.resolve('temporary'))

        when:
        getBucketOperations().delete('default')

        then:
        !Files.exists(defaultBucketPath)
        !Files.exists(objectMetadataDirectory)
        !Files.exists(multipartDirectory)
        !Files.exists(snapshotDirectory)
        !ctx.getBean(LocalStorageObjectMetadataOperations).retrieve('delete-me.txt').present
    }
}
