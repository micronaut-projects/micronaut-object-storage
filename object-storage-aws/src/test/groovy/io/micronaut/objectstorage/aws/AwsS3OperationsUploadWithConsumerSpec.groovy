package io.micronaut.objectstorage.aws

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
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
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import spock.lang.Specification

import java.nio.file.Path

@Property(name = "micronaut.object-storage.aws.default.bucket", value = "profile-pictures-bucket")
@Property(name = "spec.name", value = SPEC_NAME)
@MicronautTest
class AwsS3OperationsUploadWithConsumerSpec extends Specification {

    private static final String SPEC_NAME = "AwsS3OperationsUploadWithConsumerSpec"

    @Inject
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse> objectStorage

    @Inject
    S3ClientReplacement s3ClientReplacement

    void "consumer accept is invoked"() {
        given:
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        UploadRequest uploadRequest = UploadRequest.fromPath(path)

        when:
//tag::consumer[]
        objectStorage.upload(uploadRequest, builder -> {
                builder.acl(ObjectCannedACL.PUBLIC_READ)
        });
//end::consumer[]
        then:
        s3ClientReplacement.request
        ObjectCannedACL.PUBLIC_READ == s3ClientReplacement.request.acl()
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(S3Client)
    @Singleton
    static class S3ClientReplacement implements S3Client {
        PutObjectRequest request

        @Override
        PutObjectResponse putObject(PutObjectRequest putObjectRequest, RequestBody requestBody)
                throws AwsServiceException, SdkClientException, S3Exception {
            this.request = putObjectRequest
            null
        }

        @Override
        String serviceName() {
            's3-client-replacement'
        }
        @Override
        void close() {}
    }
}
