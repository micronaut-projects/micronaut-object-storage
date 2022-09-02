package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

abstract class AbstractOracleCloudStorageSpec extends ObjectStorageOperationsSpecification<PutObjectResponse> implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    OracleCloudStorageOperations oracleCloudStorageOperations

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
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse> getObjectStorage() {
        return oracleCloudStorageOperations
    }

    @Override
    String assertThatETagIsValid(PutObjectResponse putObjectResponse) {
        putObjectResponse.getETag()
    }

    void setup() {
        def builder = CreateBucketDetails.builder()
                .compartmentId(System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID'))
                .name(BUCKET_NAME)
        if (System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID')) {
            builder.compartmentId(System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID'))
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
}
