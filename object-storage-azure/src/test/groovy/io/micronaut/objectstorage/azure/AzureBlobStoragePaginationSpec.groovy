package io.micronaut.objectstorage.azure

import com.azure.core.http.HttpHeaders
import com.azure.core.http.HttpRequest
import com.azure.core.http.rest.PagedResponse
import com.azure.core.util.IterableStream
import com.azure.storage.blob.models.BlobItem
import io.micronaut.objectstorage.request.ListObjectsRequest
import spock.lang.Specification

class AzureBlobStoragePaginationSpec extends Specification {

    void "it lists a native Azure page with prefix page size and opaque continuation token"() {
        given:
        def operations = new TestAzureBlobStorageOperations([
                new RecordedPageCall('animals/', 'opaque-token-1', 2, pagedResponse([
                        blobItem('animals/cat.txt'),
                        blobItem('animals/dog.txt')
                ], 'opaque-token-2'))
        ])

        when:
        def page = operations.listObjects(new ListObjectsRequest(2, 'animals/', 'opaque-token-1'))

        then:
        page.keys == ['animals/cat.txt', 'animals/dog.txt']
        page.continuationToken.orElse(null) == 'opaque-token-2'
    }

    void "it preserves service order on terminal Azure pages and keeps bounded page sizes"() {
        given:
        def operations = new TestAzureBlobStorageOperations([
                new RecordedPageCall('animals/', null, 2, pagedResponse([
                        blobItem('animals/zebra.txt'),
                        blobItem('animals/ant.txt')
                ], null))
        ])

        when:
        def page = operations.listObjects(new ListObjectsRequest(2, 'animals/'))

        then:
        page.keys == ['animals/zebra.txt', 'animals/ant.txt']
        page.keys.size() == 2
        page.continuationToken.empty
    }

    void "legacy listObjects exhausts paginated Azure listing with internal page size 1000"() {
        given:
        def operations = new TestAzureBlobStorageOperations([
                new RecordedPageCall(null, null, 1000, pagedResponse([
                        blobItem('alpha'),
                        blobItem('beta')
                ], 'token-1')),
                new RecordedPageCall(null, 'token-1', 1000, pagedResponse([
                        blobItem('gamma')
                ], null))
        ])

        when:
        def keys = operations.listObjects()

        then:
        keys == ['alpha', 'beta', 'gamma'] as LinkedHashSet
    }

    void "it returns an empty portable page when Azure yields no native page"() {
        given:
        def operations = new TestAzureBlobStorageOperations([])

        when:
        def page = operations.listObjects(new ListObjectsRequest(2, 'animals/'))

        then:
        page.keys.empty
        page.continuationToken.empty
    }

    private static BlobItem blobItem(String name) {
        new BlobItem().setName(name)
    }

    private static PagedResponse<BlobItem> pagedResponse(List<BlobItem> elements, String continuationToken) {
        new TestPagedResponse(elements, continuationToken)
    }

    private static final class TestAzureBlobStorageOperations extends AzureBlobStorageOperations {
        private final Iterator<RecordedPageCall> calls

        TestAzureBlobStorageOperations(List<RecordedPageCall> calls) {
            super(null)
            this.calls = calls.iterator()
        }

        @Override
        protected Iterable<PagedResponse<BlobItem>> listNativeBlobs(String prefix, String continuationToken, int pageSize) {
            if (!calls.hasNext()) {
                return []
            }
            RecordedPageCall call = calls.next()
            assert prefix == call.expectedPrefix
            assert continuationToken == call.expectedContinuationToken
            assert pageSize == call.expectedPageSize
            [call.response]
        }
    }

    private static final class RecordedPageCall {
        final String expectedPrefix
        final String expectedContinuationToken
        final int expectedPageSize
        final PagedResponse<BlobItem> response

        RecordedPageCall(String expectedPrefix, String expectedContinuationToken, int expectedPageSize, PagedResponse<BlobItem> response) {
            this.expectedPrefix = expectedPrefix
            this.expectedContinuationToken = expectedContinuationToken
            this.expectedPageSize = expectedPageSize
            this.response = response
        }
    }

    private static final class TestPagedResponse implements PagedResponse<BlobItem> {
        private final List<BlobItem> elements
        private final String continuationToken

        TestPagedResponse(List<BlobItem> elements, String continuationToken) {
            this.elements = elements
            this.continuationToken = continuationToken
        }

        @Override
        int getStatusCode() {
            200
        }

        @Override
        HttpHeaders getHeaders() {
            new HttpHeaders()
        }

        @Override
        HttpRequest getRequest() {
            null
        }

        @Override
        List<BlobItem> getValue() {
            elements
        }

        @Override
        IterableStream<BlobItem> getElements() {
            IterableStream.of(elements)
        }

        @Override
        String getContinuationToken() {
            continuationToken
        }

        @Override
        void close() {
        }
    }
}
