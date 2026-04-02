package io.micronaut.objectstorage.aws

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.Optional

@Property(name = "micronaut.object-storage.aws.default.bucket", value = "profile-pictures-bucket")
@Property(name = "spec.name", value = SPEC_NAME)
@MicronautTest
class AwsS3OperationsUploadWithConsumerSpec extends Specification {

    private static final String SPEC_NAME = "AwsS3OperationsUploadWithConsumerSpec"

    @Inject
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> objectStorage

    @Inject
    S3ClientReplacement s3ClientReplacement

    @Inject
    InputStreamMapperReplacement inputStreamMapperReplacement

    void "consumer accept is invoked"() {
        given:
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        UploadRequest uploadRequest = UploadRequest.fromPath(path)

        when:
        objectStorage.upload(uploadRequest, builder -> {
                builder.acl(ObjectCannedACL.PUBLIC_READ)
        });

        then:
        s3ClientReplacement.request
        ObjectCannedACL.PUBLIC_READ == s3ClientReplacement.request.acl()
    }

    void "known-length uploads use the streaming AWS request body path"() {
        given:
        byte[] bytes = "stream-body".bytes
        UploadRequest uploadRequest = new StubUploadRequest("stream.txt", bytes, true)

        when:
        objectStorage.upload(uploadRequest)

        then:
        inputStreamMapperReplacement.invocationCount == 0
        s3ClientReplacement.requestBody
        s3ClientReplacement.requestBody.optionalContentLength().present
        s3ClientReplacement.requestBody.contentLength() == bytes.length
        s3ClientReplacement.requestBody.contentStreamProvider().newStream().bytes == bytes
    }

    void "unknown-length uploads keep the byte-array fallback"() {
        given:
        byte[] bytes = "fallback-body".bytes
        UploadRequest uploadRequest = new StubUploadRequest("fallback.txt", bytes, false)

        when:
        objectStorage.upload(uploadRequest)

        then:
        inputStreamMapperReplacement.invocationCount == 1
        inputStreamMapperReplacement.lastBytes == bytes
        s3ClientReplacement.requestBody
        s3ClientReplacement.requestBody.optionalContentLength().present
        s3ClientReplacement.requestBody.contentLength() == bytes.length
        s3ClientReplacement.requestBody.contentStreamProvider().newStream().bytes == bytes
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(S3Client)
    @Singleton
    static class S3ClientReplacement implements S3Client {
        PutObjectRequest request
        RequestBody requestBody

        @Override
        PutObjectResponse putObject(PutObjectRequest putObjectRequest, RequestBody requestBody)
                throws AwsServiceException, SdkClientException, S3Exception {
            this.request = putObjectRequest
            this.requestBody = requestBody
            return PutObjectResponse.builder().eTag("eTag").build()
        }

        @Override
        String serviceName() {
            's3-client-replacement'
        }
        @Override
        void close() {}
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(InputStreamMapper)
    @Singleton
    static class InputStreamMapperReplacement implements InputStreamMapper {
        int invocationCount
        byte[] lastBytes

        @Override
        byte[] toByteArray(InputStream inputStream) {
            invocationCount++
            lastBytes = inputStream.bytes
            return lastBytes
        }
    }

    private static final class StubUploadRequest implements UploadRequest {
        private final String key
        private final byte[] bytes
        private final boolean knownLength

        StubUploadRequest(String key, byte[] bytes, boolean knownLength) {
            this.key = key
            this.bytes = bytes
            this.knownLength = knownLength
        }

        @Override
        Optional<String> getContentType() {
            return Optional.of("text/plain")
        }

        @Override
        String getKey() {
            return key
        }

        @Override
        Optional<Long> getContentSize() {
            return knownLength ? Optional.of(bytes.length as long) : Optional.empty()
        }

        @Override
        InputStream getInputStream() {
            return new ByteArrayInputStream(bytes)
        }
    }
}
