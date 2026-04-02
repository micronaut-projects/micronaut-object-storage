package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

abstract class AbstractOracleCloudBucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    OracleCloudBucketOperations oracleCloudBucketOperations

    @Inject
    OracleCloudStorageOperations oracleCloudStorageOperations

    @Inject
    OracleCloudStorageConfiguration configuration

    @Inject
    ObjectStorage client

    @Override
    Map<String, String> getProperties() {
        [
            (PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME,
            (PREFIX + '.' + OBJECT_STORAGE_NAME + '.compartment-id'): System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID')
        ]
    }

    void setup() {
        def builder = CreateBucketDetails.builder()
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
    BucketOperations<?> getBucketOperations() {
        return oracleCloudBucketOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return oracleCloudStorageOperations
    }
}
