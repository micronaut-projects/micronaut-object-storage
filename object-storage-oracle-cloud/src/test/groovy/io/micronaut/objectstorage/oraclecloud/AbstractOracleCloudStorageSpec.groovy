package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

import java.util.stream.IntStream

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

abstract class AbstractOracleCloudStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

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
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, ?> getObjectStorage() {
        return oracleCloudStorageOperations
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

    def 'many bucket entries list'() {
        when:
        def names = IntStream.range(0, 40).mapToObj { it.toString() }.toList()
        for (def name : names) {
            oracleCloudStorageOperations.upload(UploadRequest.fromBytes(name.bytes, name))
        }

        then:
        oracleCloudStorageOperations.listObjects().containsAll(names)

        cleanup:
        for (def name : names) {
            try {
                oracleCloudStorageOperations.delete(name)
            } catch (ObjectStorageException ignored) {
            }
        }
    }

    void cleanup() {
        client.deleteBucket(DeleteBucketRequest.builder()
                .namespaceName(configuration.getNamespace())
                .bucketName(BUCKET_NAME)
                .build())
    }
}
