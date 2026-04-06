package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

import static io.micronaut.objectstorage.aws.AwsS3Configuration.PREFIX

abstract class AbstractAwsS3Spec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AwsS3Operations awsS3Bucket

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AwsS3BucketOperations awsS3BucketOperations

    void setup() {
        awsS3BucketOperations.create(BUCKET_NAME)
    }

    void cleanup() {
        if (awsS3BucketOperations.exists(BUCKET_NAME)) {
            awsS3BucketOperations.delete(BUCKET_NAME)
        }
    }

    @Override
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> getObjectStorage() {
        return awsS3Bucket
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return awsS3BucketOperations
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }
}
