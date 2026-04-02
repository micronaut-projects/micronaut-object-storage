package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import io.micronaut.objectstorage.response.PresignedUpload
import spock.lang.Specification
import software.amazon.awssdk.http.SdkHttpFullRequest
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

import java.time.Duration
import java.time.Instant

class AwsS3PresignedUploadSpec extends Specification {

    void "it creates a portable presigned upload for amazon s3"() {
        given:
        AwsS3Configuration configuration = new AwsS3Configuration("default")
        configuration.bucket = "profile-pictures"
        PutObjectPresignRequest capturedRequest = null
        S3Presigner presigner = [
            presignPutObject: { Object arg ->
                PutObjectPresignRequest request = arg instanceof PutObjectPresignRequest
                    ? (PutObjectPresignRequest) arg
                    : buildRequest(arg)
                capturedRequest = request
                PresignedPutObjectRequest.builder()
                    .expiration(Instant.parse("2026-04-02T12:00:00Z"))
                    .isBrowserExecutable(false)
                    .signedHeaders([
                        "content-type": ["image/png"],
                        "x-amz-meta-owner": ["alice"]
                    ])
                    .httpRequest(SdkHttpFullRequest.builder()
                        .method(SdkHttpMethod.PUT)
                        .uri(URI.create("https://profile-pictures.s3.amazonaws.com/avatars/alice.png?X-Amz-Signature=test"))
                        .putHeader("content-type", "image/png")
                        .putHeader("x-amz-meta-owner", "alice")
                        .build())
                    .build()
            },
            close: {}
        ] as S3Presigner
        AwsS3Operations operations = new TestAwsS3Operations(
            configuration,
            Mock(S3Client),
            Mock(InputStreamMapper),
            presigner
        )
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5))
        request.contentType = "image/png"
        request.contentLength = 128
        request.metadata = [owner: "alice"]

        when:
        PresignedUpload response = operations.createPresignedUpload(request).get()

        then:
        capturedRequest.signatureDuration() == Duration.ofMinutes(5)
        capturedRequest.putObjectRequest().bucket() == "profile-pictures"
        capturedRequest.putObjectRequest().key() == "avatars/alice.png"
        capturedRequest.putObjectRequest().contentType() == "image/png"
        capturedRequest.putObjectRequest().contentLength() == 128
        capturedRequest.putObjectRequest().metadata() == [owner: "alice"]
        response.method == "PUT"
        response.uri == URI.create("https://profile-pictures.s3.amazonaws.com/avatars/alice.png?X-Amz-Signature=test")
        response.headers["content-type"] == ["image/png"]
        response.headers["x-amz-meta-owner"] == ["alice"]
        response.expiration == Instant.parse("2026-04-02T12:00:00Z")
    }

    void "it wraps presigner bootstrap failures as object storage exceptions"() {
        given:
        AwsS3Configuration configuration = new AwsS3Configuration("default")
        configuration.bucket = "profile-pictures"
        AwsS3Operations operations = new AwsS3Operations(
            configuration,
            Mock(S3Client),
            Mock(InputStreamMapper)
        )
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5))

        when:
        operations.createPresignedUpload(request)

        then:
        ObjectStorageException e = thrown()
        e.message.contains("pre-signed upload request")
    }

    private static final class TestAwsS3Operations extends AwsS3Operations {
        private final S3Presigner presigner

        TestAwsS3Operations(AwsS3Configuration configuration,
                            S3Client s3Client,
                            InputStreamMapper inputStreamMapper,
                            S3Presigner presigner) {
            super(configuration, s3Client, inputStreamMapper)
            this.presigner = presigner
        }

        @Override
        protected S3Presigner createS3Presigner() {
            return presigner
        }
    }

    private static PutObjectPresignRequest buildRequest(Object consumer) {
        PutObjectPresignRequest.Builder builder = PutObjectPresignRequest.builder()
        consumer.accept(builder)
        return builder.build()
    }
}
