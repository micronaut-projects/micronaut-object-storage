package io.micronaut.objectstorage.relational

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class RelationalStoragePaginationSpec extends spock.lang.Specification implements TestPropertyProvider {

    private static final List<String> SEEDED_KEYS = [
        'animals/cat.txt',
        'animals/dog.txt',
        'animals/mammals/fox.txt',
        'animals/zebra.txt',
        'plants/oak.txt',
        'plants/pine.txt',
        'tools/axe.txt',
    ]

    @Inject
    RelationalStorageOperations operations

    @Override
    Map<String, String> getProperties() {
        [
            (RelationalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (RelationalStorageConfiguration.PREFIX + '.default.table-name'): 'pagination_relational_objects',
        ]
    }

    void setup() {
        SEEDED_KEYS.each { key ->
            operations.upload(UploadRequest.fromBytes('seed'.bytes, key, 'text/plain'))
        }
    }

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
    }

    void 'paginated listing is bounded prefix filtered and keeps continuation tokens external'() {
        when:
        def firstPage = operations.listObjects(new ListObjectsRequest(2, 'animals/'))
        def secondPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', firstPage.continuationToken.orElse(null)))
        def replayedTerminalPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', secondPage.keys.last()))

        then:
        firstPage.keys == ['animals/cat.txt', 'animals/dog.txt']
        firstPage.continuationToken.orElse(null) == 'animals/dog.txt'
        secondPage.keys == ['animals/mammals/fox.txt', 'animals/zebra.txt']
        secondPage.continuationToken.empty
        replayedTerminalPage.keys.empty
        replayedTerminalPage.continuationToken.empty
    }

    void 'continuation token is a strict lower bound and listObjects returns all visible keys in order'() {
        when:
        def page = operations.listObjects(new ListObjectsRequest(2, null, 'animals/dog.txt'))
        def allKeys = operations.listObjects() as List

        then:
        page.keys == ['animals/mammals/fox.txt', 'animals/zebra.txt']
        page.continuationToken.orElse(null) == 'animals/zebra.txt'
        allKeys == [
            'animals/cat.txt',
            'animals/dog.txt',
            'animals/mammals/fox.txt',
            'animals/zebra.txt',
            'plants/oak.txt',
            'plants/pine.txt',
            'tools/axe.txt',
        ]
    }
}
