package io.micronaut.objectstorage

import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsResponse
import io.micronaut.objectstorage.multipart.MultipartPart
import io.micronaut.objectstorage.multipart.MultipartUploadHandle
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import spock.lang.Subject

@Subject([MultipartPart, MultipartUploadHandle])
class MultipartUploadContractSpec extends Specification {

    void "multipart part validates portable part metadata"() {
        expect:
        new MultipartPart(1, "etag-1", 0).getChecksum() == Optional.empty()
        new MultipartPart(1, "etag-1", 128, "").getChecksum() == Optional.empty()
        new MultipartPart(1, "etag-1", 128, "checksum-1").getChecksum() == Optional.of("checksum-1")

        when:
        new MultipartPart(partNumber, "etag-1", size)

        then:
        thrown(IllegalArgumentException)

        where:
        partNumber | size
        0          | 1
        1          | -1
    }

    void "create multipart upload request normalizes optional fields and protects metadata"() {
        given:
        Map<String, String> metadata = [owner: "micronaut"]

        when:
        def request = new CreateMultipartUploadRequest("videos/demo.mp4", "", metadata)
        metadata.project = "object-storage"

        then:
        request.key == "videos/demo.mp4"
        request.getContentType() == Optional.empty()
        request.metadata == [owner: "micronaut"]

        when:
        request.metadata.team = "framework"

        then:
        thrown(UnsupportedOperationException)
    }

    void "upload part request validates part number and payload key"() {
        given:
        def upload = new MultipartUploadHandle("videos/demo.mp4", "upload-1")

        expect:
        new UploadPartRequest(upload, 1, UploadRequest.fromBytes("first".bytes, "videos/demo.mp4")).partNumber == 1

        when:
        new UploadPartRequest(upload, partNumber, UploadRequest.fromBytes("first".bytes, key))

        then:
        thrown(expected)

        where:
        partNumber | key                  || expected
        0          | "videos/demo.mp4"    || IllegalArgumentException
        1          | "videos/other.mp4"   || IllegalArgumentException
    }

    void "complete multipart upload request requires a non-empty ordered immutable manifest"() {
        given:
        def upload = new MultipartUploadHandle("videos/demo.mp4", "upload-1")
        def firstPart = new MultipartPart(1, "etag-1", 128)
        def secondPart = new MultipartPart(2, "etag-2", 64)
        List<MultipartPart> parts = [firstPart, secondPart]

        when:
        def request = new CompleteMultipartUploadRequest(upload, parts)
        parts.clear()

        then:
        request.parts*.partNumber == [1, 2]

        when:
        request.parts << new MultipartPart(3, "etag-3", 32)

        then:
        thrown(UnsupportedOperationException)

        when:
        new CompleteMultipartUploadRequest(upload, [])

        then:
        thrown(IllegalArgumentException)

        when:
        new CompleteMultipartUploadRequest(upload, [secondPart, firstPart])

        then:
        thrown(IllegalArgumentException)
    }

    void "list multipart parts request and response expose immutable pagination semantics"() {
        given:
        def upload = new MultipartUploadHandle("videos/demo.mp4", "upload-1")
        def firstPart = new MultipartPart(1, "etag-1", 128)
        List<MultipartPart> parts = [firstPart]

        when:
        def request = new ListMultipartPartsRequest(upload, 2, "")
        def response = new ListMultipartPartsResponse(parts, "")
        parts << new MultipartPart(2, "etag-2", 64)

        then:
        request.upload == upload
        request.pageSize == 2
        request.getContinuationToken() == Optional.empty()
        response.parts == [firstPart]
        response.getContinuationToken() == Optional.empty()

        when:
        response.parts << new MultipartPart(3, "etag-3", 32)

        then:
        thrown(UnsupportedOperationException)

        when:
        new ListMultipartPartsRequest(upload, 0)

        then:
        thrown(IllegalArgumentException)

        expect:
        new ListMultipartPartsRequest(upload, 1, "token-1").getContinuationToken() == Optional.of("token-1")
        new ListMultipartPartsResponse([firstPart], "token-2").getContinuationToken() == Optional.of("token-2")
    }
}
