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

import io.micronaut.objectstorage.bucket.BucketEntry
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.UUID

abstract class BucketOperationsSpecification extends Specification {
    void 'it can create retrieve and delete buckets'() {
        given:
        def bucketOps = getBucketOperations()
        def name = newBucketName()
        PollingConditions conditions = new PollingConditions(timeout: 30)

        expect:
        !bucketOps.retrieve(name).present
        !bucketOps.exists(name)

        when:
        bucketOps.create(name)

        then:
        conditions.eventually {
            assert bucketOps.exists(name)
            assert bucketOps.retrieve(name).present
        }

        and:
        BucketEntry<?> bucket = bucketOps.retrieve(name).orElseThrow()

        then:
        bucket.name() == name
        bucket.nativeEntry()

        when:
        bucketOps.delete(name)

        then:
        conditions.eventually {
            assert !bucketOps.exists(name)
            assert !bucketOps.retrieve(name).present
        }

        cleanup:
        if (bucketOps.exists(name)) {
            bucketOps.delete(name)
        }
    }

    void 'it does not retarget the configured object storage bean while managing other buckets'() {
        given:
        def bucketOps = getBucketOperations()
        def storage = getObjectStorage()
        def bucketName = newBucketName()
        def key = "bucket-ops-regression-${UUID.randomUUID()}"
        PollingConditions conditions = new PollingConditions(timeout: 30)

        when:
        storage.upload(UploadRequest.fromBytes("foo".bytes, key))
        bucketOps.create(bucketName)

        then:
        conditions.eventually {
            assert bucketOps.exists(bucketName)
        }
        storage.exists(key)
        new String(storage.retrieve(key).orElseThrow().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"

        when:
        bucketOps.delete(bucketName)

        then:
        conditions.eventually {
            assert !bucketOps.exists(bucketName)
        }
        storage.exists(key)
        new String(storage.retrieve(key).orElseThrow().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"

        cleanup:
        if (storage.exists(key)) {
            storage.delete(key)
        }
        if (bucketOps.exists(bucketName)) {
            bucketOps.delete(bucketName)
        }
    }

    abstract BucketOperations<?> getBucketOperations()

    abstract ObjectStorageOperations<?, ?, ?> getObjectStorage()

    String newBucketName() {
        "micronaut-object-storage-${UUID.randomUUID().toString().replace('-', '').substring(0, 20)}"
    }
}
