package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.BucketMetadataOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.metadata.BucketMetadataOperations

import java.nio.file.Files
import java.nio.file.Path

class LocalStorageBucketMetadataOperationsSpec extends BucketMetadataOperationsSpecification {

    private Path rootDirectory
    private Path defaultBucketPath
    private ApplicationContext ctx

    void setup() {
        rootDirectory = Files.createTempDirectory('LocalStorageBucketMetadataOperationsSpec')
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
    BucketMetadataOperations<?> getBucketMetadataOperations() {
        ctx.getBean(LocalStorageBucketMetadataOperations)
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        ctx.getBean(LocalStorageOperations)
    }

    void 'it rejects invalid bucket metadata names consistently'() {
        when:
        getBucketMetadataOperations().retrieve(name)

        then:
        thrown IllegalArgumentException

        when:
        getBucketMetadataOperations().delete(name)

        then:
        thrown IllegalArgumentException

        where:
        name << ['..', 'foo/bar', '.mn-storage', '.metadata', '.Metadata']
    }
}
