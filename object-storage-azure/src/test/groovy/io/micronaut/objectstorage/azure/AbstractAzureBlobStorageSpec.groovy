package io.micronaut.objectstorage.azure

import com.azure.core.http.rest.Response
import com.azure.storage.blob.models.BlockBlobItem
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Shared

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

abstract class AbstractAzureBlobStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {
    public static final String CONTAINER_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AzureBlobStorageOperations azureBlobContainer

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    @Shared
    AzureBlobBucketOperations azureBlobBucketOperations

    void setupSpec() {
        azureBlobBucketOperations.create(CONTAINER_NAME)
    }

    void cleanupSpec() {
        if (azureBlobBucketOperations.exists(CONTAINER_NAME)) {
            azureBlobBucketOperations.delete(CONTAINER_NAME)
        }
    }

    @Override
    ObjectStorageOperations<?, Response<BlockBlobItem>, ?> getObjectStorage() {
        azureBlobContainer
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        azureBlobBucketOperations
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.container'): CONTAINER_NAME]
    }
}
