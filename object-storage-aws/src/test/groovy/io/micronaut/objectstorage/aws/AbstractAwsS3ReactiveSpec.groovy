package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest

import static io.micronaut.objectstorage.aws.AwsS3Configuration.PREFIX

abstract class AbstractAwsS3ReactiveSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    S3Client s3

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    ReactiveObjectStorageOperations<?, ?, ?> awsS3Bucket

    void setup() {
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build() as CreateBucketRequest)
    }

    void cleanup() {
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build() as DeleteBucketRequest)
    }

    @Override
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return awsS3Bucket
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }
}
