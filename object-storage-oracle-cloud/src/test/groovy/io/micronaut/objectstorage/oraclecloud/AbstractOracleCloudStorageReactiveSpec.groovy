package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

abstract class AbstractOracleCloudStorageReactiveSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    ReactiveObjectStorageOperations<?, ?, ?> oracleCloudStorageOperations

    @Inject
    OracleCloudStorageConfiguration configuration

    @Inject
    ObjectStorage client

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }

    @Override
    @NonNull
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return oracleCloudStorageOperations
    }

    void setup() {
        def compartmentId = System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID')
        if (!compartmentId) {
            throw new IllegalStateException('ORACLE_CLOUD_TEST_COMPARTMENT_ID environment variable must be set for Oracle Cloud storage tests')
        }
        def builder = CreateBucketDetails.builder()
            .compartmentId(compartmentId)
            .name(BUCKET_NAME)
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
}
