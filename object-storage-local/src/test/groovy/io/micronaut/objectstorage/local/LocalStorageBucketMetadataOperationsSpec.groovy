package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.BucketMetadataOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

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

    void 'stale temp cleanup does not delete bucket metadata sidecars with temp-like names'() {
        given:
        String victimName = 'micronaut-object-storage-local-bucket-metadata-victim.tmp'
        String otherName = 'unrelated'
        getBucketOperations().create(victimName)
        getBucketOperations().create(otherName)
        def metadataOperations = getBucketMetadataOperations()
        metadataOperations.save(new BucketMetadataWrite(victimName, [role: 'victim'], [:]))
        Path victimMetadataFile = bucketMetadataFile(victimName)
        Files.setLastModifiedTime(victimMetadataFile, staleFileTime())

        when:
        metadataOperations.save(new BucketMetadataWrite(otherName, [role: 'other'], [:]))

        then:
        Files.exists(victimMetadataFile)
        metadataOperations.retrieve(victimName).get().metadata == [role: 'victim']
    }

    void 'bucket metadata save removes stale matching temporary files'() {
        given:
        String bucketName = 'cleanup'
        getBucketOperations().create(bucketName)
        def metadataOperations = getBucketMetadataOperations()
        metadataOperations.save(new BucketMetadataWrite(bucketName, [role: 'original'], [:]))
        Path temporaryDirectory = bucketMetadataTemporaryDirectory(bucketName)
        Files.createDirectories(temporaryDirectory)
        Path staleTemporaryFile = temporaryDirectory.resolve('micronaut-object-storage-local-bucket-metadata-stale.tmp')
        Path freshTemporaryFile = temporaryDirectory.resolve('micronaut-object-storage-local-bucket-metadata-fresh.tmp')
        Path unrelatedTemporaryFile = temporaryDirectory.resolve('unrelated-stale.tmp')
        Files.writeString(staleTemporaryFile, 'stale')
        Files.writeString(freshTemporaryFile, 'fresh')
        Files.writeString(unrelatedTemporaryFile, 'unrelated')
        Files.setLastModifiedTime(staleTemporaryFile, staleFileTime())
        Files.setLastModifiedTime(unrelatedTemporaryFile, staleFileTime())

        when:
        metadataOperations.save(new BucketMetadataWrite(bucketName, [role: 'updated'], [:]))

        then:
        !Files.exists(staleTemporaryFile)
        Files.readString(freshTemporaryFile) == 'fresh'
        Files.readString(unrelatedTemporaryFile) == 'unrelated'
        metadataOperations.retrieve(bucketName).get().metadata == [role: 'updated']
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

    private Path bucketMetadataFile(String name) {
        rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve(LocalStorageLayout.BUCKETS_DIRECTORY)
            .resolve(name)
    }

    private Path bucketMetadataTemporaryDirectory(String name) {
        rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.TEMPORARY_DIRECTORY)
            .resolve(name)
            .resolve(LocalStorageLayout.BUCKET_METADATA_TEMPORARY_DIRECTORY)
    }

    private static FileTime staleFileTime() {
        FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
    }
}
