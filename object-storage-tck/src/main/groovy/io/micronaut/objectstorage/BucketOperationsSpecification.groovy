package io.micronaut.objectstorage

import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

abstract class BucketOperationsSpecification extends Specification {
    void crud() {
        given:
        def bucketOps = getBucketOperations()
        def initBuckets = bucketOps.listBuckets()

        when:
        def name1 = "micronaut-objectstorage-test-" + UUID.randomUUID()
        bucketOps.createBucket(name1)
        then:
        bucketOps.listBuckets() == initBuckets + [name1]

        when:
        def bucket = bucketOps.storageForBucket(name1)
        then:
        bucket.listObjects().isEmpty()

        when:
        bucket.upload(UploadRequest.fromBytes("foo".bytes, "key"))
        then:
        bucket.listObjects() == Set.of("key")
        new String(bucket.retrieve("key").get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"

        when:
        bucket.copy("key", "key2")
        TimeUnit.SECONDS.sleep(5) // copy is not instant on OCI apparently
        then:
        bucket.listObjects() == Set.of("key", "key2")
        new String(bucket.retrieve("key2").get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"

        when:
        bucket.delete("key")
        then:
        bucket.listObjects() == Set.of("key2")

        when:
        bucket.delete("key2")
        bucketOps.deleteBucket(name1)
        then:
        bucketOps.listBuckets() == initBuckets
    }

    abstract BucketOperations<?, ?, ?> getBucketOperations()
}
