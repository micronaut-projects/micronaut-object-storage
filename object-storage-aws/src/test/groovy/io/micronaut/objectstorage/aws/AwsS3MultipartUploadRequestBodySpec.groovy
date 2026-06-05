package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.multipart.MultipartUploadHandle
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListPartsResponse
import software.amazon.awssdk.services.s3.model.Part
import software.amazon.awssdk.services.s3.model.UploadPartResponse

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class AwsS3MultipartUploadRequestBodySpec extends Specification {

    void "it streams multipart parts when the upload request declares a content size"() {
        given:
        S3Client s3Client = Mock()
        InputStreamMapper inputStreamMapper = Mock()
        AwsS3Operations operations = new AwsS3Operations(configuration(), s3Client, inputStreamMapper)
        UploadRequest uploadRequest = new StreamUploadRequest("videos/demo.mp4", [1, 2, 3, 4] as byte[], 4L)

        when:
        def response = operations.uploadPart(new UploadPartRequest(new MultipartUploadHandle("videos/demo.mp4", "upload-1"), 1, uploadRequest))

        then:
        1 * s3Client.uploadPart({
            it.bucket() == "multipart-bucket" &&
                it.key() == "videos/demo.mp4" &&
                it.uploadId() == "upload-1" &&
                it.partNumber() == 1 &&
                it.contentLength() == 4L
        }, _) >> UploadPartResponse.builder()
            .eTag("etag-1")
            .build()
        0 * inputStreamMapper._
        response.part.partNumber == 1
        response.part.size == 4L
        response.part.ETag == "etag-1"
    }

    void "it rejects streaming multipart parts without a content size"() {
        given:
        S3Client s3Client = Mock()
        InputStreamMapper inputStreamMapper = Mock()
        AwsS3Operations operations = new AwsS3Operations(configuration(), s3Client, inputStreamMapper)
        UploadRequest uploadRequest = new StreamUploadRequest("videos/demo.mp4", [1, 2, 3, 4] as byte[], null)

        when:
        operations.uploadPart(new UploadPartRequest(new MultipartUploadHandle("videos/demo.mp4", "upload-1"), 1, uploadRequest))

        then:
        ObjectStorageException e = thrown()
        e.message == "Multipart uploads require UploadRequest#getContentSize() for streaming requests"
        0 * s3Client._
        0 * inputStreamMapper._
    }

    void "it binds multipart continuation tokens to the upload and page size"() {
        given:
        S3Client s3Client = Mock()
        AwsS3Operations operations = new AwsS3Operations(configuration(), s3Client, Mock(InputStreamMapper))
        MultipartUploadHandle upload = new MultipartUploadHandle("videos/demo.mp4", "upload-1")

        when:
        def firstPage = operations.listParts(new ListMultipartPartsRequest(upload, 2))
        String continuationToken = firstPage.getContinuationToken().orElseThrow()
        def secondPage = operations.listParts(new ListMultipartPartsRequest(upload, 2, continuationToken))

        then:
        1 * s3Client.listParts({
            it.bucket() == "multipart-bucket" &&
                it.key() == "videos/demo.mp4" &&
                it.uploadId() == "upload-1" &&
                it.maxParts() == 2 &&
                it.partNumberMarker() == null
        }) >> ListPartsResponse.builder()
            .parts(part(1), part(2))
            .nextPartNumberMarker(2)
            .build()
        1 * s3Client.listParts({
            it.bucket() == "multipart-bucket" &&
                it.key() == "videos/demo.mp4" &&
                it.uploadId() == "upload-1" &&
                it.maxParts() == 2 &&
                it.partNumberMarker() == 2
        }) >> ListPartsResponse.builder()
            .parts(part(3))
            .build()
        firstPage.parts*.partNumber == [1, 2]
        secondPage.parts*.partNumber == [3]

        when:
        operations.listParts(new ListMultipartPartsRequest(new MultipartUploadHandle("videos/other.mp4", "upload-1"), 2, continuationToken))

        then:
        ObjectStorageException keyMismatch = thrown()
        keyMismatch.message == "AWS S3 multipart continuation token does not match the current request"
        0 * s3Client._

        when:
        operations.listParts(new ListMultipartPartsRequest(upload, 3, continuationToken))

        then:
        ObjectStorageException pageSizeMismatch = thrown()
        pageSizeMismatch.message == "AWS S3 multipart continuation token does not match the current request"
        0 * s3Client._
    }

    void "it rejects invalid multipart continuation tokens before listing parts"() {
        given:
        S3Client s3Client = Mock()
        AwsS3Operations operations = new AwsS3Operations(configuration(), s3Client, Mock(InputStreamMapper))
        MultipartUploadHandle upload = new MultipartUploadHandle("videos/demo.mp4", "upload-1")

        when:
        operations.listParts(new ListMultipartPartsRequest(upload, 2, "part-2"))

        then:
        ObjectStorageException e = thrown()
        e.message == "Invalid AWS S3 multipart continuation token"
        0 * s3Client._
    }

    void "it rejects non-positive multipart continuation markers before listing parts"() {
        given:
        S3Client s3Client = Mock()
        AwsS3Operations operations = new AwsS3Operations(configuration(), s3Client, Mock(InputStreamMapper))
        MultipartUploadHandle upload = new MultipartUploadHandle("videos/demo.mp4", "upload-1")

        when:
        operations.listParts(new ListMultipartPartsRequest(
            upload,
            2,
            continuationToken("videos/demo.mp4", "upload-1", 2, marker)
        ))

        then:
        ObjectStorageException e = thrown()
        e.message == "Invalid AWS S3 multipart continuation token"
        0 * s3Client._

        where:
        marker << [0, -1]
    }

    private static AwsS3Configuration configuration() {
        AwsS3Configuration configuration = new AwsS3Configuration("default")
        configuration.bucket = "multipart-bucket"
        return configuration
    }

    private static Part part(int partNumber) {
        return Part.builder()
            .partNumber(partNumber)
            .eTag("etag-" + partNumber)
            .size(partNumber)
            .build()
    }

    private static String continuationToken(String key, String uploadId, int pageSize, int marker) {
        String payload = new StringJoiner("\n")
            .add(encodeTokenPart(key))
            .add(encodeTokenPart(uploadId))
            .add(encodeTokenPart(Integer.toString(pageSize)))
            .add(encodeTokenPart(Integer.toString(marker)))
            .toString()
        return "aws-s3-multipart:v1:" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
    }

    private static String encodeTokenPart(String value) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }

    private static final class StreamUploadRequest implements UploadRequest {
        private final String key
        private final byte[] bytes
        private final Long contentSize

        private StreamUploadRequest(String key, byte[] bytes, Long contentSize) {
            this.key = key
            this.bytes = bytes
            this.contentSize = contentSize
        }

        @Override
        Optional<String> getContentType() {
            return Optional.empty()
        }

        @Override
        String getKey() {
            return key
        }

        @Override
        Optional<Long> getContentSize() {
            return Optional.ofNullable(contentSize)
        }

        @Override
        InputStream getInputStream() {
            return new ByteArrayInputStream(bytes)
        }
    }
}
