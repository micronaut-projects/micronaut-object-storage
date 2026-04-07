package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

import java.util.stream.IntStream

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

abstract class AbstractOracleCloudStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    OracleCloudStorageOperations oracleCloudStorageOperations

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    OracleCloudBucketOperations oracleCloudBucketOperations

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
        oracleCloudBucketOperations.create(BUCKET_NAME)
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
        if (oracleCloudBucketOperations.exists(BUCKET_NAME)) {
            oracleCloudBucketOperations.delete(BUCKET_NAME)
        }
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return oracleCloudBucketOperations
    }
}
