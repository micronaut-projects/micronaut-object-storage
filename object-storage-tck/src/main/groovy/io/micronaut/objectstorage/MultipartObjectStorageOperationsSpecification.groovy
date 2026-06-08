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

import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsResponse
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.objectstorage.multipart.MultipartPart
import io.micronaut.objectstorage.multipart.MultipartUploadHandle
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.multipart.UploadPartResponse
import io.micronaut.objectstorage.request.UploadRequest
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

abstract class MultipartObjectStorageOperationsSpecification extends ObjectStorageOperationsSpecification {

    private static final String PART_ONE_TEXT = 'micro'
    private static final String PART_TWO_TEXT = 'naut'
    private static final String PART_THREE_TEXT = '-framework'

    void 'it can create upload parts complete and retrieve multipart object'() {
        given:
        MultipartObjectStorageOperations<?, ?, ?> storage = getMultipartObjectStorage()
        ObjectStorageOperations<?, ?, ?> objectStorage = getObjectStorage()
        PollingConditions conditions = new PollingConditions(timeout: 10)
        String key = "multipart/${System.nanoTime()}/combined.txt"
        MultipartUploadHandle upload = null
        boolean completed = false
        byte[] nonFinalPartBytes = nonFinalMultipartPartBytes()

        when:
        upload = storage.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE, METADATA)).upload
        UploadPartResponse<?> firstPart = storage.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(nonFinalPartBytes, key, CONTENT_TYPE)))
        UploadPartResponse<?> secondPart = storage.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes(PART_TWO_TEXT.bytes, key, CONTENT_TYPE)))
        def response = storage.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [firstPart.part, secondPart.part]))
        completed = true

        then:
        response.ETag
        conditions.eventually {
            assert objectStorage.retrieve(key).present
        }
        ObjectStorageEntry<?> objectStorageEntry = objectStorage.retrieve(key).get()
        byte[] objectBytes = objectStorageEntry.inputStream.bytes
        objectBytes.length == nonFinalPartBytes.length + PART_TWO_TEXT.bytes.length
        objectBytes[0] == (byte) 'a'
        objectBytes[nonFinalPartBytes.length - 1] == (byte) 'a'
        new String(objectBytes, nonFinalPartBytes.length, PART_TWO_TEXT.bytes.length, StandardCharsets.UTF_8) == PART_TWO_TEXT
        if (emulatorSupportsMetadata()) {
            assert objectStorageEntry.metadata == METADATA
        }
        objectStorageEntry.contentType == Optional.of(CONTENT_TYPE)

        cleanup:
        if (upload != null && !completed) {
            storage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
        }
        if (objectStorage.exists(key)) {
            objectStorage.delete(key)
        }
    }

    void 'it can list multipart parts with portable pagination semantics'() {
        given:
        MultipartObjectStorageOperations<?, ?, ?> storage = getMultipartObjectStorage()
        String key = "multipart/${System.nanoTime()}/parts.txt"
        def upload = storage.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload
        byte[] nonFinalPartBytes = nonFinalMultipartPartBytes()

        and:
        storage.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(nonFinalPartBytes, key, CONTENT_TYPE)))
        storage.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes(nonFinalPartBytes, key, CONTENT_TYPE)))
        storage.uploadPart(new UploadPartRequest(upload, 3, UploadRequest.fromBytes(PART_THREE_TEXT.bytes, key, CONTENT_TYPE)))

        when:
        ListMultipartPartsRequest firstPageRequest = new ListMultipartPartsRequest(upload, 2)
        ListMultipartPartsResponse firstPage = storage.listParts(firstPageRequest)
        ListMultipartPartsResponse replayedFirstPage = storage.listParts(firstPageRequest)
        List<ListMultipartPartsResponse> pages = collectMultipartPages(storage, firstPageRequest)
        List<Integer> partNumbers = pages.collectMany { page -> page.parts*.partNumber }

        then:
        pages
        pages.every { it.parts.size() <= firstPageRequest.pageSize }
        replayedFirstPage.parts*.partNumber == firstPage.parts*.partNumber
        replayedFirstPage.getContinuationToken().present == firstPage.getContinuationToken().present
        pages.first().parts*.partNumber == firstPage.parts*.partNumber
        pages.first().getContinuationToken().present == firstPage.getContinuationToken().present
        pages.dropRight(1).every { it.parts }
        adjacentMultipartPages(pages).every { pair -> !pair[0].parts*.partNumber.intersect(pair[1].parts*.partNumber) }
        partNumbers == [1, 2, 3]
        !pages.last().getContinuationToken().present

        cleanup:
        storage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
    }

    void 'abort multipart upload is safe to retry and removes the in progress upload'() {
        given:
        MultipartObjectStorageOperations<?, ?, ?> storage = getMultipartObjectStorage()
        ObjectStorageOperations<?, ?, ?> objectStorage = getObjectStorage()
        String key = "multipart/${System.nanoTime()}/abort.txt"
        def upload = storage.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload
        UploadPartResponse<?> uploadedPart = storage.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(PART_ONE_TEXT.bytes, key, CONTENT_TYPE)))

        when:
        storage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
        storage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))

        then:
        !objectStorage.exists(key)

        when:
        storage.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [uploadedPart.part]))

        then:
        thrown(ObjectStorageException)
    }

    void 'complete multipart upload rejects misordered manifests'() {
        given:
        MultipartObjectStorageOperations<?, ?, ?> storage = getMultipartObjectStorage()
        String key = "multipart/${System.nanoTime()}/misordered.txt"
        def upload = storage.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload
        UploadPartResponse<?> firstPart = storage.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(PART_ONE_TEXT.bytes, key, CONTENT_TYPE)))
        UploadPartResponse<?> secondPart = storage.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes(PART_TWO_TEXT.bytes, key, CONTENT_TYPE)))

        when:
        new CompleteMultipartUploadRequest(upload, [secondPart.part, firstPart.part])

        then:
        thrown(IllegalArgumentException)

        cleanup:
        storage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
    }

    void 'complete multipart upload rejects missing parts from the manifest'() {
        given:
        MultipartObjectStorageOperations<?, ?, ?> storage = getMultipartObjectStorage()
        String key = "multipart/${System.nanoTime()}/missing-part.txt"
        def upload = storage.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload
        UploadPartResponse<?> uploadedPart = storage.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(nonFinalMultipartPartBytes(), key, CONTENT_TYPE)))
        MultipartPart missingPart = new MultipartPart(2, 'missing-etag', PART_TWO_TEXT.bytes.length)

        when:
        storage.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [uploadedPart.part, missingPart]))

        then:
        thrown(ObjectStorageException)

        cleanup:
        storage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
    }

    abstract MultipartObjectStorageOperations<?, ?, ?> getMultipartObjectStorage()

    protected abstract int nonFinalMultipartPartSizeBytes()

    private byte[] nonFinalMultipartPartBytes() {
        ('a' * nonFinalMultipartPartSizeBytes()).bytes
    }

    private static List<ListMultipartPartsResponse> collectMultipartPages(MultipartObjectStorageOperations<?, ?, ?> storage,
                                                                         ListMultipartPartsRequest request) {
        List<ListMultipartPartsResponse> pages = []
        ListMultipartPartsRequest currentRequest = request
        Set<String> seenTokens = [] as Set
        int maxPages = 8
        while (true) {
            ListMultipartPartsResponse page = storage.listParts(currentRequest)
            pages << page
            Optional<String> continuationToken = page.getContinuationToken()
            if (!continuationToken.present) {
                return pages
            }
            assert seenTokens.add(continuationToken.get()): 'Repeated continuation token returned while collecting multipart pages'
            assert pages.size() <= maxPages: 'Exceeded maximum expected multipart page count while collecting pages'
            currentRequest = new ListMultipartPartsRequest(request.upload, request.pageSize, continuationToken.get())
        }
    }

    private static List<List<ListMultipartPartsResponse>> adjacentMultipartPages(List<ListMultipartPartsResponse> pages) {
        (0..<Math.max(pages.size() - 1, 0)).collect { index ->
            [pages[index], pages[index + 1]]
        }
    }
}
