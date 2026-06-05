package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.MultipartObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

import static io.micronaut.objectstorage.aws.AwsS3Configuration.PREFIX

abstract class AbstractAwsS3MultipartSpec extends MultipartObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'
    private static final int AWS_NON_FINAL_MULTIPART_PART_SIZE_BYTES = 5 * 1024 * 1024

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
        awsS3BucketOperations.delete(BUCKET_NAME)
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
    MultipartObjectStorageOperations<?, ?, ?> getMultipartObjectStorage() {
        return awsS3Bucket
    }

    @Override
    protected int nonFinalMultipartPartSizeBytes() {
        AWS_NON_FINAL_MULTIPART_PART_SIZE_BYTES
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }
}
