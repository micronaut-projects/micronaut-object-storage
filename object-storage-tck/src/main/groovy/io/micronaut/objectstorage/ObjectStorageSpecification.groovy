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

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files


abstract class ObjectStorageSpecification extends Specification {

    def "it can upload, get and delete object from file"() {
        given:
        def tempFilePath = Files.createTempFile("test-file", "txt")
        def tempFileName = tempFilePath.getFileName().toString()
        tempFilePath.toFile().text = "micronaut"

        when: 'put file to object storage'
        UploadRequest uploadRequest = UploadRequest.fromFile(tempFilePath)
        def uploadResponse = getObjectStorage().put(uploadRequest)

        then:
        uploadResponse
        uploadResponse.ETag

        when: 'get file based on path'
        Optional<ObjectStorageEntry> objectStorageEntry = getObjectStorage().get(tempFileName)

        then:
        objectStorageEntry.isPresent()
        objectStorageEntry.get().key == tempFileName

        when: 'the file has same content'
        String text = new BufferedReader(
                new InputStreamReader(objectStorageEntry.get().inputStream, StandardCharsets.UTF_8))
                .text

        then:
        text
        text == "micronaut"

        when: 'delete the file on object storage'
        getObjectStorage().delete(tempFileName)

        then:
        noExceptionThrown()

        when: 'get file based on path'
        objectStorageEntry = getObjectStorage().get(tempFileName)

        then:
        !objectStorageEntry.isPresent()
    }

    abstract ObjectStorage getObjectStorage()
}
