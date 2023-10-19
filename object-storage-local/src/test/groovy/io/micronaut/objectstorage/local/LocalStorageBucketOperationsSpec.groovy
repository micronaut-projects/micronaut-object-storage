package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import javax.swing.plaf.basic.BasicCheckBoxUI
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class LocalStorageBucketOperationsSpec extends Specification {
    def 'crud bucket'() {
        given:
        def tmpDynamic = Files.createTempDirectory("LocalStorageBucketOperationsSpec")
        def tmpStatic1 = Files.createTempDirectory("LocalStorageBucketOperationsSpec")
        def tmpStatic2 = Files.createTempDirectory("LocalStorageBucketOperationsSpec")
        def ctx = ApplicationContext.run([
                "micronaut.object-storage.local-bucket-operations.directory": tmpDynamic.toString(),
                "micronaut.object-storage.local.s1.path": tmpStatic1.toString(),
                "micronaut.object-storage.local.s2.path": tmpStatic2.toString()
        ])
        def bucketOps = ctx.getBean(BucketOperations)

        expect:
        bucketOps instanceof LocalStorageBucketOperations
        bucketOps.listBuckets() == Set.of(
                tmpStatic1.toAbsolutePath().toString(),
                tmpStatic2.toAbsolutePath().toString()
        )

        when:
        bucketOps.createBucket("foo")
        then:
        bucketOps.listBuckets() == Set.of(
                tmpStatic1.toAbsolutePath().toString(),
                tmpStatic2.toAbsolutePath().toString(),
                "foo"
        )

        expect:
        testBucket(bucketOps.storageForBucket("foo"))
        testBucket(bucketOps.storageForBucket(tmpStatic1.toAbsolutePath().toString()))
        testBucket(bucketOps.storageForBucket(tmpStatic2.toAbsolutePath().toString()))

        when:
        bucketOps.deleteBucket("foo")
        then:
        bucketOps.listBuckets() == Set.of(
                tmpStatic1.toAbsolutePath().toString(),
                tmpStatic2.toAbsolutePath().toString()
        )

        when:
        bucketOps.deleteBucket(tmpStatic1.toAbsolutePath().toString())
        then:
        thrown IllegalArgumentException

        when:
        bucketOps.createBucket("..")
        then:
        thrown IllegalArgumentException

        when:
        bucketOps.createBucket("foo/bar")
        then:
        thrown IllegalArgumentException

        cleanup:
        ctx.close()
        LocalStorageBucketOperations.deleteRecursively(tmpDynamic)
        LocalStorageBucketOperations.deleteRecursively(tmpStatic1)
        LocalStorageBucketOperations.deleteRecursively(tmpStatic2)
    }

    private void testBucket(ObjectStorageOperations<?, ?, ?> ops) {
        assert ops.listObjects() == Set.<String>of()
        ops.upload(UploadRequest.fromBytes("foo".bytes, "key"))
        assert ops.listObjects() == Set.of("key")
        assert new String(ops.retrieve("key").get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"
        ops.copy("key", "key2")
        assert ops.listObjects() == Set.of("key", "key2")
        assert new String(ops.retrieve("key2").get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"
        ops.delete("key")
        assert ops.listObjects() == Set.of("key2")
    }
}
