package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.MultipartUploadHandle
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.UploadPartResponse

import java.io.ByteArrayInputStream
import java.io.InputStream

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

    private static AwsS3Configuration configuration() {
        AwsS3Configuration configuration = new AwsS3Configuration("default")
        configuration.bucket = "multipart-bucket"
        return configuration
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
