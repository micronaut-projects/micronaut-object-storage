package io.micronaut.objectstorage.googlecloud

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobInfo
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Shared

import static io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration.PREFIX

abstract class AbstractGoogleCloudStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Shared
    @Inject
    GoogleCloudStorageOperations cloudObjectStorage

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    @Shared
    GoogleCloudBucketOperations googleCloudBucketOperations

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket'): BUCKET_NAME]
    }

    ObjectStorageOperations<BlobInfo.Builder, Blob, ?> getObjectStorage() {
        return cloudObjectStorage
    }

    void setupSpec() {
        googleCloudBucketOperations.create(BUCKET_NAME)
    }

    void cleanupSpec() {
        if (googleCloudBucketOperations.exists(BUCKET_NAME)) {
            googleCloudBucketOperations.delete(BUCKET_NAME)
        }
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return googleCloudBucketOperations
    }
}
