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


import io.micronaut.objectstorage.request.PresignRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.PresignResponse
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

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

    @IgnoreIf({ !instance.emulatorSupportsPresign() })
    void 'it can generate and invalidate a presigned URL'(TestFile testFile) {
        given: 'an uploaded object'
        ObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        UploadRequest uploadRequest = testFile.uploadRequest
        storage.upload(uploadRequest)

        when: 'requesting a presigned URL'
        PresignRequest presignRequest = PresignRequest.builder(uploadRequest.key, PresignRequest.Operation.DOWNLOAD).build()
        PresignResponse presignResponse = storage.presign(presignRequest)

        then: 'a valid URL and expiration are returned'
        assert presignResponse.url
        assert presignResponse.url.toString().startsWith('http')
        assert presignResponse.expiration.isAfter(Instant.now())

        when: 'using the presigned URL to download'
        String downloaded
        def url = new URL(presignResponse.url.toString())
        downloaded = url.text

        then: 'the downloaded content matches the stored object'
        assert downloaded == TEXT

        when: 'explicitly invalidating the URL'
        if (emulatorSupportsPresignInvalidate()) {
            storage.invalidatePresignedRequest(presignResponse.url)
        }

        then: 'no exception is thrown'
        noExceptionThrown()
        storage.delete(uploadRequest.key)

        where:
        testFile << [
                createTestFile(),
                createTestFile('dir'),
                createTestFile('dir/subdir'),
        ]
    }

    @IgnoreIf({ !instance.emulatorSupportsPresignUpload() })
    void 'it can upload an object using a presigned URL'(TestFile testFile) {
        given: "A presigned upload URL"
        ObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        String newKey = "presigned-upload"
        if (testFile.uploadRequest.key.contains("/")) {
            String dirKey = testFile.uploadRequest.key.substring(0, testFile.uploadRequest.key.lastIndexOf('/'))
            newKey = dirKey + "/presigned-upload"
        }
        PresignRequest presignRequest = PresignRequest.builder(newKey, PresignRequest.Operation.UPLOAD).build()
        PresignResponse presignResponse = storage.presign(presignRequest)

        when: "Uploading to the presigned URL"
        def url = new URL(presignResponse.url.toString())
        def connection = (HttpURLConnection) url.openConnection()
        connection.setDoOutput(true)
        connection.setRequestMethod("PUT")
        connection.setRequestProperty("Content-Type", CONTENT_TYPE)
        connection.setRequestProperty("x-ms-blob-type", "BlockBlob")
        def bytes = Files.readAllBytes(testFile.path)
        connection.setRequestProperty("Content-Length", bytes.length.toString())
        connection.getOutputStream().withStream { os ->
            os.write(bytes)
        }
        int responseCode = connection.responseCode
        println("response is $responseCode")

        then: "The upload returns a successful response"
        assert responseCode in [200, 201, 204]

        when: "Retrieving the uploaded file"
        Optional<ObjectStorageEntry<?>> objectStorageEntry  = storage.retrieve(newKey)

        then: "The content is correct"
        assert objectStorageEntry.isPresent()
        assert objectStorageEntry.get().inputStream.text == TEXT

        cleanup:
        storage.delete(newKey)

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

    boolean emulatorSupportsPresign() {
        true
    }

    boolean emulatorSupportsPresignInvalidate() {
        emulatorSupportsPresign()
    }

    boolean emulatorSupportsPresignUpload() {
        emulatorSupportsPresign()
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
