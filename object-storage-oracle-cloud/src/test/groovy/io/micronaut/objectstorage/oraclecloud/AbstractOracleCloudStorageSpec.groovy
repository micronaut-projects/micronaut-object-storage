package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

abstract class AbstractOracleCloudStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Inject
    OracleCloudStorageOperations oracleCloudStorageOperations

    @Inject
    OracleCloudStorageConfiguration configuration

    @Inject
    ObjectStorage client

    @Override
    Map<String, String> getProperties() {
        [
                ("${OracleCloudStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString()): BUCKET_NAME,
        ]
    }

    @Override
    ObjectStorageOperations getObjectStorage() {
        return oracleCloudStorageOperations
    }

    void setup() {
        def builder = CreateBucketDetails.builder()
                .compartmentId(System.getenv("ORACLE_CLOUD_TEST_COMPARTMENT_ID"))
                .name(BUCKET_NAME)
        if (System.getenv("ORACLE_CLOUD_TEST_COMPARTMENT_ID")) {
            builder.compartmentId(System.getenv("ORACLE_CLOUD_TEST_COMPARTMENT_ID"))
        }
        CreateBucketRequest request = CreateBucketRequest.builder()
                .namespaceName(configuration.getNamespace())
                .createBucketDetails(builder.build())
                .build()
        client.createBucket(request)

    }

    void cleanup() {
        DeleteBucketRequest request = DeleteBucketRequest.builder()
                .namespaceName(configuration.getNamespace())
                .bucketName(BUCKET_NAME)
                .build()
        client.deleteBucket(request)
    }
}
