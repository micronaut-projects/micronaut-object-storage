/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.UUID

abstract class BucketMetadataOperationsSpecification extends Specification {

    private static final String OWNER = "cliponaut"

    void 'it can save retrieve update and delete bucket metadata'() {
        given:
        BucketOperations<?> bucketOperations = getBucketOperations()
        BucketMetadataOperations<?> metadataOperations = getBucketMetadataOperations()
        String bucketName = newBucketName()

        when:
        bucketOperations.create(bucketName)

        then:
        new PollingConditions(timeout: 30).eventually {
            assert bucketOperations.exists(bucketName)
        }

        when:
        metadataOperations.save(new BucketMetadataWrite(bucketName, [region: "test"], [owner: OWNER]))

        then:
        metadataOperations.retrieve(bucketName).present
        metadataOperations.retrieve(bucketName).get().metadata == [region: "test"]
        metadataOperations.retrieve(bucketName).get().attributes == [owner: OWNER]

        when:
        metadataOperations.save(new BucketMetadataWrite(bucketName, [region: "prod"], [owner: "core"]))

        then:
        metadataOperations.retrieve(bucketName).get().metadata == [region: "prod"]
        metadataOperations.retrieve(bucketName).get().attributes == [owner: "core"]

        when:
        metadataOperations.delete(bucketName)

        then:
        !metadataOperations.retrieve(bucketName).present

        cleanup:
        metadataOperations.delete(bucketName)
        if (bucketOperations.exists(bucketName)) {
            bucketOperations.delete(bucketName)
        }
    }

    void 'bucket metadata operations do not retarget the configured object storage bean'() {
        given:
        BucketOperations<?> bucketOperations = getBucketOperations()
        BucketMetadataOperations<?> metadataOperations = getBucketMetadataOperations()
        ObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        String bucketName = newBucketName()
        String key = "bucket-metadata-${UUID.randomUUID()}.txt"
        PollingConditions conditions = new PollingConditions(timeout: 30)

        when:
        storage.upload(UploadRequest.fromBytes("foo".bytes, key))
        bucketOperations.create(bucketName)
        metadataOperations.save(new BucketMetadataWrite(bucketName, [region: "test"], [owner: OWNER]))

        then:
        conditions.eventually {
            assert bucketOperations.exists(bucketName)
            assert metadataOperations.retrieve(bucketName).present
        }
        storage.exists(key)
        new String(storage.retrieve(key).orElseThrow().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "foo"

        cleanup:
        metadataOperations.delete(bucketName)
        if (bucketOperations.exists(bucketName)) {
            bucketOperations.delete(bucketName)
        }
        if (storage.exists(key)) {
            storage.delete(key)
        }
    }

    abstract BucketOperations<?> getBucketOperations()

    abstract BucketMetadataOperations<?> getBucketMetadataOperations()

    abstract ObjectStorageOperations<?, ?, ?> getObjectStorage()

    String newBucketName() {
        "micronaut-object-storage-${UUID.randomUUID().toString().replace('-', '').substring(0, 20)}"
    }
}
