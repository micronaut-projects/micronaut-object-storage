package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import io.micronaut.objectstorage.MultipartObjectStorageOperations
import io.micronaut.objectstorage.MultipartObjectStorageOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

abstract class AbstractOracleCloudMultipartSpec extends MultipartObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    ObjectStorage client

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    OracleCloudStorageConfiguration configuration

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    OracleCloudStorageOperations oracleCloudStorageOperations

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    OracleCloudBucketOperations oracleCloudBucketOperations

    void setup() {
        CreateBucketDetails.Builder builder = CreateBucketDetails.builder()
            .name(BUCKET_NAME)
        if (configuration.getCompartmentId()) {
            builder.compartmentId(configuration.getCompartmentId())
        }
        client.createBucket(CreateBucketRequest.builder()
            .namespaceName(configuration.getNamespace())
            .createBucketDetails(builder.build())
            .build())
    }

    void cleanup() {
        client.deleteBucket(DeleteBucketRequest.builder()
            .namespaceName(configuration.getNamespace())
            .bucketName(BUCKET_NAME)
            .build())
    }

    @Override
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> getObjectStorage() {
        return oracleCloudStorageOperations
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return oracleCloudBucketOperations
    }

    @Override
    MultipartObjectStorageOperations<?, ?, ?> getMultipartObjectStorage() {
        return oracleCloudStorageOperations
    }

    @Override
    Map<String, String> getProperties() {
        [
            (PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME,
            (PREFIX + '.' + OBJECT_STORAGE_NAME + '.compartment-id'): System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID')
        ]
    }
}
