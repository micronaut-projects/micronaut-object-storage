package io.micronaut.objectstorage.request

import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import spock.lang.Specification

class BytesUploadRequestSpec extends Specification {

    private static final String KEY = "micronaut.txt"
    private static final byte[] BYTES = ObjectStorageOperationsSpecification.TEXT.bytes

    void "it can be created from bytes and a key"() {
        given:
        byte[] bytes = BYTES

        when:
        BytesUploadRequest request = UploadRequest.fromBytes(bytes, KEY) as BytesUploadRequest

        then:
        assertThatRequestIsCorrect(request, "text/plain")
    }

    void "it can be created from bytes, a key and content type"() {
        given:
        byte[] bytes = BYTES
        String contentType = "text/enriched"

        when:
        BytesUploadRequest request = UploadRequest.fromBytes(bytes, KEY, contentType) as BytesUploadRequest

        then:
        assertThatRequestIsCorrect(request, contentType)
    }

    private void assertThatRequestIsCorrect(BytesUploadRequest request, String expectedContentType) {
        request.with {
            assert key == KEY
            assert bytes == BYTES
            assert contentSize.present
            assert contentSize.get() == ObjectStorageOperationsSpecification.TEXT.length() as long
            assert contentType.present
            assert contentType.get() == expectedContentType
            assert inputStream.text == ObjectStorageOperationsSpecification.TEXT
        }
    }

}
