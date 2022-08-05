package io.micronaut.objectstorage.googlecloud

import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

abstract class AbstractGoogleCloudStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Inject
    GoogleCloudStorageOperations cloudObjectStorage

    @Inject
    Storage storage

    @Override
    Map<String, String> getProperties() {
        [
                ("${GoogleCloudStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString()): BUCKET_NAME,
        ]
    }

    ObjectStorageOperations getObjectStorage() {
        return cloudObjectStorage
    }

    void setup() {
        storage.create(BucketInfo.newBuilder(BUCKET_NAME).build());
    }

    void cleanup() {
        storage.get(BUCKET_NAME).delete()
    }

}
