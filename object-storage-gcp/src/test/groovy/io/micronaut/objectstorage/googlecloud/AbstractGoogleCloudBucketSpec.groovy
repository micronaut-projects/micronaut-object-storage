package io.micronaut.objectstorage.googlecloud

import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Shared

import static io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration.PREFIX

abstract class AbstractGoogleCloudBucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    GoogleCloudBucketOperations googleCloudBucketOperations

    @Inject
    GoogleCloudStorageOperations googleCloudStorageOperations

    @Shared
    @Inject
    Storage storage

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }

    void setupSpec() {
        storage.create(BucketInfo.newBuilder(BUCKET_NAME).build())
    }

    void cleanupSpec() {
        storage.get(BUCKET_NAME).delete()
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return googleCloudBucketOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return googleCloudStorageOperations
    }
}
