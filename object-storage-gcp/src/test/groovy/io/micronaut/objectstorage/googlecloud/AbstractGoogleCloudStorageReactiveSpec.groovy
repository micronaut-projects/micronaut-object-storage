package io.micronaut.objectstorage.googlecloud

import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Shared

import static io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration.PREFIX

abstract class AbstractGoogleCloudStorageReactiveSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Shared
    @Inject
    ReactiveObjectStorageOperations<?, ?, ?> cloudObjectStorage

    @Shared
    @Inject
    Storage storage

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }

    @Override
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return cloudObjectStorage
    }

    void setupSpec() {
        storage.create(BucketInfo.newBuilder(BUCKET_NAME).build())
    }

    void cleanupSpec() {
        storage.get(BUCKET_NAME).delete()
    }
}
