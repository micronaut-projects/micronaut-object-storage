package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest

import static io.micronaut.objectstorage.aws.AwsS3Configuration.PREFIX

abstract class AbstractAwsS3BucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    S3Client s3

    @Inject
    AwsS3BucketOperations awsBucketOperations

    @Inject
    AwsS3Operations awsS3Operations

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }

    void setup() {
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build() as CreateBucketRequest)
    }

    void cleanup() {
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build() as DeleteBucketRequest)
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return awsBucketOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return awsS3Operations
    }
}
