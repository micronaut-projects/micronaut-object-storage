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

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.ListObjectsResponse
import io.micronaut.objectstorage.response.UploadResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration

abstract class ReactiveObjectStorageOperationsSpecification extends Specification {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final String ANIMALS_PREFIX = 'animals/'
    public static final String TEXT = ObjectStorageOperationsSpecification.TEXT
    public static final String NEW_TEXT = ObjectStorageOperationsSpecification.NEW_TEXT
    public static final Map<String, String> METADATA = ObjectStorageOperationsSpecification.METADATA
    public static final String CONTENT_TYPE = ObjectStorageOperationsSpecification.CONTENT_TYPE
    private static final List<String> PAGINATED_LISTING_KEYS = [
        'animals/cat.txt',
        'animals/dog.txt',
        'animals/mammals/fox.txt',
        'plants/oak.txt',
        'plants/pine.txt',
    ]

    void 'it can upload, get and delete object reactively'(ObjectStorageOperationsSpecification.TestFile testFile) {
        given:
        ReactiveObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        String key = testFile.uploadRequest.key
        PollingConditions conditions = new PollingConditions(timeout: 30)

        when:
        UploadRequest uploadRequest = testFile.uploadRequest
        uploadRequest.metadata = METADATA
        uploadRequest.contentType = CONTENT_TYPE

        then:
        !awaitOne(storage.exists(uploadRequest.key))
        !awaitOne(storage.listObjects())

        when:
        UploadResponse response = awaitOne(storage.upload(uploadRequest))

        then:
        response.ETag
        awaitOne(storage.exists(uploadRequest.key))
        awaitOne(storage.listObjects()).size() == 1
        awaitOne(storage.listObjects()).first() == uploadRequest.key

        when:
        Optional<ObjectStorageEntry<?>> objectStorageEntry = awaitOne(storage.retrieve(key))

        then:
        objectStorageEntry.present
        objectStorageEntry.get().key == key
        objectStorageEntry.get().nativeEntry
        if (emulatorSupportsMetadata()) {
            assert objectStorageEntry.get().metadata == METADATA
        }
        objectStorageEntry.get().contentType == Optional.of(CONTENT_TYPE)

        when:
        String text = objectStorageEntry.get().inputStream.text

        then:
        text
        text == TEXT

        when:
        if (emulatorSupportsUpdate()) {
            testFile.path.toFile().text = NEW_TEXT
            if (key.contains('/')) {
                String dirKey = key.substring(0, key.lastIndexOf('/'))
                uploadRequest = UploadRequest.fromPath(testFile.path, dirKey)
            } else {
                uploadRequest = UploadRequest.fromPath(testFile.path)
            }
            uploadRequest.metadata = METADATA
            uploadRequest.contentType = CONTENT_TYPE
            response = awaitOne(storage.upload(uploadRequest))
        }

        then:
        if (emulatorSupportsUpdate()) {
            assert response.ETag
            assert awaitOne(storage.exists(uploadRequest.key))
            assert awaitOne(storage.listObjects()).size() == 1
            assert awaitOne(storage.listObjects()).first() == uploadRequest.key
        }

        when:
        if (emulatorSupportsUpdate()) {
            objectStorageEntry = awaitOne(storage.retrieve(key))
        }

        then:
        if (emulatorSupportsUpdate()) {
            assert objectStorageEntry.get().inputStream.text == NEW_TEXT
            assert objectStorageEntry.get().metadata == METADATA
            assert objectStorageEntry.get().contentType.get() == CONTENT_TYPE
        }

        when:
        String newKey = "newFile.txt"
        if (emulatorSupportsCopy()) {
            awaitCompletion(storage.copy(key, newKey))
        }

        then:
        if (emulatorSupportsCopy()) {
            assert awaitOne(storage.exists(key))
            conditions.eventually {
                awaitOne(storage.exists(newKey))
            }
            assert awaitOne(storage.exists(newKey))
            assert awaitOne(storage.listObjects()).size() == 2
        }

        and:
        if (emulatorSupportsCopy()) {
            assert awaitOne(storage.retrieve(newKey)).get().metadata == METADATA
            assert awaitOne(storage.retrieve(newKey)).get().contentType.get() == CONTENT_TYPE
        }

        when:
        awaitOne(storage.delete(key))
        if (emulatorSupportsCopy()) {
            awaitOne(storage.delete(newKey))
        }

        then:
        noExceptionThrown()
        !awaitOne(storage.exists(key))
        if (emulatorSupportsCopy()) {
            assert !awaitOne(storage.exists(newKey))
        }
        !awaitOne(storage.listObjects())

        when:
        objectStorageEntry = awaitOne(storage.retrieve(key))

        then:
        !objectStorageEntry.present

        where:
        testFile << [
            ObjectStorageOperationsSpecification.createTestFile(),
            ObjectStorageOperationsSpecification.createTestFile('dir'),
            ObjectStorageOperationsSpecification.createTestFile('dir/subdir'),
        ]
    }

    void 'it can list objects reactively with portable pagination semantics'() {
        given:
        ReactiveObjectStorageOperations<?, ?, ?> storage = getObjectStorage()
        List<ObjectStorageOperationsSpecification.TestFile> testFiles = PAGINATED_LISTING_KEYS.collect { key ->
            ObjectStorageOperationsSpecification.createTestFileForKey(key)
        }
        testFiles.each { awaitOne(storage.upload(it.uploadRequest)) }

        when:
        ListObjectsRequest firstPageRequest = new ListObjectsRequest(2)
        ListObjectsResponse firstPage = awaitOne(storage.listObjects(firstPageRequest))
        ListObjectsResponse replayedFirstPage = awaitOne(storage.listObjects(firstPageRequest))
        List<ListObjectsResponse> pages = collectPages(storage, firstPageRequest)
        List<String> combinedKeys = pages.collectMany { it.keys }

        then:
        pages
        pages.every { it.keys.size() <= firstPageRequest.pageSize }
        replayedFirstPage.keys == firstPage.keys
        replayedFirstPage.continuationToken == firstPage.continuationToken
        pages.first().keys == firstPage.keys
        pages.first().continuationToken == firstPage.continuationToken
        pages.dropRight(1).every { it.keys }
        adjacentPages(pages).every { pair -> !pair[0].keys.intersect(pair[1].keys) }
        combinedKeys.toSet() == PAGINATED_LISTING_KEYS.toSet()
        combinedKeys.size() == PAGINATED_LISTING_KEYS.size()
        !pages.last().continuationToken.present

        when:
        ListObjectsRequest animalsRequest = new ListObjectsRequest(2, ANIMALS_PREFIX)
        List<ListObjectsResponse> animalPages = collectPages(storage, animalsRequest)
        List<String> animalKeys = animalPages.collectMany { it.keys }

        then:
        animalPages
        animalPages.every { it.keys.size() <= animalsRequest.pageSize }
        animalKeys.every { it.startsWith(ANIMALS_PREFIX) }
        animalKeys.toSet() == PAGINATED_LISTING_KEYS.findAll { it.startsWith(ANIMALS_PREFIX) }.toSet()
        animalKeys.size() == PAGINATED_LISTING_KEYS.count { it.startsWith(ANIMALS_PREFIX) }
        animalPages.dropRight(1).every { it.keys }
        adjacentPages(animalPages).every { pair -> !pair[0].keys.intersect(pair[1].keys) }
        !animalPages.last().continuationToken.present

        cleanup:
        testFiles.each { awaitOne(storage.delete(it.uploadRequest.key)) }
    }

    abstract ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage()

    boolean emulatorSupportsMetadata() {
        true
    }

    boolean emulatorSupportsUpdate() {
        true
    }

    boolean emulatorSupportsCopy() {
        true
    }

    protected static <T> T awaitOne(org.reactivestreams.Publisher<T> publisher) {
        Mono.from(publisher).block(AWAIT_TIMEOUT)
    }

    protected static void awaitCompletion(org.reactivestreams.Publisher<Void> publisher) {
        Flux.from(publisher).collectList().block(AWAIT_TIMEOUT)
    }

    private static List<ListObjectsResponse> collectPages(ReactiveObjectStorageOperations<?, ?, ?> storage,
                                                          ListObjectsRequest request) {
        List<ListObjectsResponse> pages = []
        ListObjectsRequest currentRequest = request
        Set<String> seenTokens = [] as Set
        int maxPages = PAGINATED_LISTING_KEYS.size() + 1
        while (true) {
            ListObjectsResponse page = awaitOne(storage.listObjects(currentRequest))
            pages << page
            Optional<String> continuationToken = page.continuationToken
            if (!continuationToken.present) {
                return pages
            }
            assert seenTokens.add(continuationToken.get()): "Repeated continuation token returned while collecting pages"
            assert pages.size() <= maxPages: "Exceeded maximum expected page count while collecting pages"
            currentRequest = new ListObjectsRequest(request.pageSize, request.prefix.orElse(null), continuationToken.get())
        }
    }

    private static List<List<ListObjectsResponse>> adjacentPages(List<ListObjectsResponse> pages) {
        (0..<Math.max(pages.size() - 1, 0)).collect { index ->
            [pages[index], pages[index + 1]]
        }
    }
}
