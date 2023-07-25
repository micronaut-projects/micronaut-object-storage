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
import spock.util.concurrent.PollingConditions

import java.nio.file.Files
import java.nio.file.Path

abstract class ObjectStorageOperationsSpecification extends Specification {

    public static final String TEXT = 'micronaut'
    public static final String NEW_TEXT = 'object-storage'
    public static final Map<String, String> METADATA = [project: "micronaut-object-storage"]
    public static final String CONTENT_TYPE = "text/plain"

    void 'it can upload, get and delete object from file'(TestFile testFile) {
        given: 'a temporary file'
        ObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        Path path = testFile.path
        String key = testFile.uploadRequest.key
        PollingConditions conditions = new PollingConditions(timeout: 30)

        when: 'creating the upload request'
        UploadRequest uploadRequest = testFile.uploadRequest
        uploadRequest.metadata = METADATA
        uploadRequest.contentType = CONTENT_TYPE

        then: 'the file does not exist yet'
        !storage.exists(uploadRequest.key)
        !storage.listObjects()

        when: 'uploading the file'
        UploadResponse response = storage.upload(uploadRequest)

        then: 'the file exists'
        response.ETag
        storage.exists(uploadRequest.key)
        storage.listObjects().size() == 1
        storage.listObjects().first() == uploadRequest.key

        when: 'retrieving the file'
        Optional<ObjectStorageEntry<?>> objectStorageEntry = storage.retrieve(key)

        then: 'the file exists'
        objectStorageEntry.isPresent()
        objectStorageEntry.get().key == key
        objectStorageEntry.get().nativeEntry
        if (emulatorSupportsMetadata()) {
            assert objectStorageEntry.get().metadata == METADATA
        }
        objectStorageEntry.get().contentType == Optional.of(CONTENT_TYPE)

        when: 'reading the file content'
        String text = objectStorageEntry.get().inputStream.text

        then: 'the content is the same as the source'
        text
        text == TEXT

        when: 'updating the file'
        if (emulatorSupportsUpdate()) {
            path.toFile().text = NEW_TEXT
            if (key.contains('/')) {
                String dirKey = key.substring(0, key.lastIndexOf('/'))
                uploadRequest = UploadRequest.fromPath(path, dirKey)
            } else {
                uploadRequest = UploadRequest.fromPath(path)
            }
            uploadRequest.metadata = METADATA
            uploadRequest.contentType = CONTENT_TYPE
            response = storage.upload(uploadRequest)
        }

        then: 'the file exists'
        if (emulatorSupportsUpdate()) {
            assert response.ETag
            assert storage.exists(uploadRequest.key)
            assert storage.listObjects().size() == 1
            assert storage.listObjects().first() == uploadRequest.key
        }

        when: 'retrieving the file'
        if (emulatorSupportsUpdate()) {
            objectStorageEntry = storage.retrieve(key)
        }

        then: 'the file has the new text and correct metadata and content type'
        if (emulatorSupportsUpdate()) {
            assert objectStorageEntry.get().inputStream.text == NEW_TEXT
            assert objectStorageEntry.get().metadata == METADATA
            assert objectStorageEntry.get().contentType.get() == CONTENT_TYPE
        }

        when: 'copying the file to a new location'
        String newKey = "newFile.txt"
        if (emulatorSupportsCopy()) {
            storage.copy(key, newKey)
        }

        then: 'the file also exists'
        if (emulatorSupportsCopy()) {
            assert storage.exists(key)
            conditions.eventually {
                storage.exists(newKey)
            }
            assert storage.exists(newKey)
            assert storage.listObjects().size() == 2
        }

        and: 'it has metadata and content type'
        if (emulatorSupportsCopy()) {
            assert storage.retrieve(newKey).get().metadata == METADATA
            assert storage.retrieve(newKey).get().contentType.get() == CONTENT_TYPE
        }

        when: 'deleting the files'
        storage.delete(key)
        if (emulatorSupportsCopy()) {
            storage.delete(newKey)
        }

        then: 'the files do not exist'
        noExceptionThrown()
        !storage.exists(key)
        if (emulatorSupportsCopy()) {
            assert !storage.exists(newKey)
        }
        !storage.listObjects()

        when: 'retrieving the file'
        objectStorageEntry = storage.retrieve(key)

        then: 'the file does not exist'
        !objectStorageEntry.isPresent()

        where:
        testFile << [
                createTestFile(),
                createTestFile('dir'),
                createTestFile('dir/subdir'),
        ]
    }

    abstract ObjectStorageOperations<?, ?, ?> getObjectStorage()

    boolean emulatorSupportsMetadata() {
        true
    }

    boolean emulatorSupportsUpdate() {
        true
    }

    boolean emulatorSupportsCopy() {
        true
    }

    static Path createTempFile() {
        Path path = Files.createTempFile('test-file', '.txt')
        path.toFile().text = TEXT
        return path
    }

    static TestFile createTestFile(String dir = null) {
        Path path = createTempFile()
        UploadRequest uploadRequest
        if (dir) {
            uploadRequest = UploadRequest.fromPath(path, dir)
        } else {
            uploadRequest = UploadRequest.fromPath(path)
        }
        return new TestFile(path: path, uploadRequest: uploadRequest)
    }

    static class TestFile {
        Path path
        UploadRequest uploadRequest
    }
}
