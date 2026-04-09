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

import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.UUID

abstract class ObjectMetadataOperationsSpecification extends Specification {

    void 'it can save retrieve update and delete object metadata without rewriting object bytes'() {
        given:
        ObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        ObjectMetadataOperations<?> metadataOperations = getObjectMetadataOperations()
        String key = "metadata-${UUID.randomUUID()}.txt"
        UploadRequest request = UploadRequest.fromBytes("micronaut".bytes, key, "text/plain")
        request.metadata = [project: "initial"]
        storage.upload(request)

        when:
        def initial = metadataOperations.retrieve(key)

        then:
        initial.present
        initial.get().metadata == [project: "initial"]
        initial.get().attributes == [:]

        when:
        metadataOperations.save(new ObjectMetadataWrite(
            key,
            [project: "updated"],
            [owner: "cliponaut"],
            initial.get().contentType(),
            initial.get().contentLength(),
            initial.get().etag(),
            initial.get().lastModified()
        ))

        then:
        metadataOperations.retrieve(key).get().metadata == [project: "updated"]
        metadataOperations.retrieve(key).get().attributes == [owner: "cliponaut"]
        storage.retrieve(key).get().metadata == [project: "updated"]
        new String(storage.retrieve(key).get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "micronaut"

        when:
        metadataOperations.delete(key)

        then:
        !metadataOperations.retrieve(key).present
        storage.retrieve(key).present
        storage.retrieve(key).get().metadata == [:]
        new String(storage.retrieve(key).get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "micronaut"

        cleanup:
        if (storage.exists(key)) {
            storage.delete(key)
        }
    }

    void 'missing object metadata returns empty'() {
        expect:
        !getObjectMetadataOperations().retrieve("missing-${UUID.randomUUID()}.txt").present
    }

    abstract ObjectStorageOperations<?, ?, ?> getObjectStorage()

    abstract ObjectMetadataOperations<?> getObjectMetadataOperations()
}
