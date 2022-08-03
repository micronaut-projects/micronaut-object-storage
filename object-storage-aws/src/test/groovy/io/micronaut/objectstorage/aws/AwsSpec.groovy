package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorage
import io.micronaut.objectstorage.ObjectStorageSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest

abstract class AwsSpec extends ObjectStorageSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Inject
    S3Client s3

    @Named(OBJECT_STORAGE_NAME)
    @Inject
    AwsS3Bucket awsS3Bucket

    void setup() {
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build() as CreateBucketRequest)
    }

    void cleanup() {
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build() as DeleteBucketRequest)
    }

    @Override
    ObjectStorage getObjectStorage() {
        return awsS3Bucket
    }

    @Override
    Map<String, String> getProperties() {
        return [
                ("${AwsS3BucketConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString()): BUCKET_NAME
        ]
    }
}
