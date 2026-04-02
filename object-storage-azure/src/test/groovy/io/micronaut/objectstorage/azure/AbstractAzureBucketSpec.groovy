package io.micronaut.objectstorage.azure

import com.azure.storage.blob.BlobServiceClient
import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Named
import jakarta.inject.Inject
import spock.lang.Shared

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

abstract class AbstractAzureBucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    public static final String CONTAINER_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    AzureBlobBucketOperations azureBucketOperations

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AzureBlobStorageOperations azureBlobStorageOperations

    @Shared
    @Inject
    BlobServiceClient blobServiceClient

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.container'): CONTAINER_NAME]
    }

    void setupSpec() {
        blobServiceClient.createBlobContainer(CONTAINER_NAME)
    }

    void cleanupSpec() {
        def containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME)
        if (containerClient.exists()) {
            containerClient.delete()
        }
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return azureBucketOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return azureBlobStorageOperations
    }
}
