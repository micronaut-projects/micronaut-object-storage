/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import static java.nio.charset.StandardCharsets.UTF_8

abstract class ObjectStorageOperationsSpecification extends Specification {

    public static final String TEXT = 'micronaut'
    public static final Map<String, String> METADATA = [project: "micronaut-object-storage"]
    public static final String CONTENT_TYPE = "text/plain"

    void 'it can upload, get and delete object from file'() {
        given:
        Path path = createTempFile()
        String tempFileName = path.getFileName().toString()

        when: 'put file to object storage'
        UploadRequest uploadRequest = UploadRequest.fromPath(path)
        uploadRequest.metadata = METADATA
        uploadRequest.contentType = CONTENT_TYPE
        UploadResponse response = getObjectStorage().upload(uploadRequest)

        then:
        response.ETag

        when: 'get file based on path'
        Optional<ObjectStorageEntry<?>> objectStorageEntry = getObjectStorage().retrieve(tempFileName)

        then:
        objectStorageEntry.isPresent()
        objectStorageEntry.get().key == tempFileName
        objectStorageEntry.get().nativeEntry
        if (supportsMetadata()) {
            assert objectStorageEntry.get().metadata == METADATA
        }
        objectStorageEntry.get().contentType == Optional.of(CONTENT_TYPE)

        when: 'the file has same content'
        String text = new BufferedReader(
                new InputStreamReader(objectStorageEntry.get().inputStream, UTF_8))
                .text

        then:
        text
        text == TEXT

        when: 'delete the file on object storage'
        getObjectStorage().delete(tempFileName)

        then:
        noExceptionThrown()

        when: 'get file based on path'
        objectStorageEntry = getObjectStorage().retrieve(tempFileName)

        then:
        !objectStorageEntry.isPresent()
    }

    abstract ObjectStorageOperations<?, ?, ?> getObjectStorage()

    boolean supportsMetadata() {
        true
    }

    static Path createTempFile() {
        Path path = Files.createTempFile('test-file', '.txt')
        path.toFile().text = TEXT
        return path
    }
}
