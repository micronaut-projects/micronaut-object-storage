/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage

import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom

abstract class BucketOperationsSpecification extends Specification {
    void crud() {
        given:
        def bucketOps = getBucketOperations()
        def initBuckets = bucketOps.listBuckets()
        PollingConditions conditions = new PollingConditions(timeout: 30)

        when:
        def name1 = "micronaut-objectstorage-test-" + ThreadLocalRandom.current().nextLong()
        bucketOps.createBucket(name1)
        then:
        conditions.eventually {
            bucketOps.listBuckets() == initBuckets + [name1]
        }

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
        then:
        conditions.eventually {
            bucket.listObjects() == Set.of("key", "key2")
        }
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
