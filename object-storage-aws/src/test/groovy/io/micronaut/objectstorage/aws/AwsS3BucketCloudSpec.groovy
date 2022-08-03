package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorage
import io.micronaut.objectstorage.ObjectStorageSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import spock.lang.Requires

@Requires({ env.AWS_ACCESS_KEY_ID && env.AWS_SECRET_ACCESS_KEY && env.AWS_REGION })
@MicronautTest
class AwsS3BucketCloudSpec extends ObjectStorageSpecification implements TestPropertyProvider {

    private static final String BUCKET_NAME = System.currentTimeMillis()

    @Named("default")
    @Inject
    AwsS3Bucket awsS3Bucket

    @Inject
    S3Client s3

    @Override
    ObjectStorage getObjectStorage() {
        return awsS3Bucket
    }

    @Override
    Map<String, String> getProperties() {
        return [
                (AwsS3BucketConfiguration.PREFIX + ".default.name"): BUCKET_NAME
        ]
    }

    void setup() {
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build() as CreateBucketRequest)
    }

    void cleanup() {
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build() as DeleteBucketRequest)
    }
}
