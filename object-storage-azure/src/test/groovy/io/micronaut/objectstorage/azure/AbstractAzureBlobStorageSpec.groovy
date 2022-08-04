package io.micronaut.objectstorage.azure

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

abstract class AbstractAzureBlobStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider{

    public static final String CONTAINER_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AzureBlobStorageOperations azureBlobContainer

    @Inject
    BlobContainerClient blobContainerClient

    @Inject
    BlobServiceClient blobServiceClient

    void setup() {
        blobServiceClient.createBlobContainer(CONTAINER_NAME)
    }

    void cleanup() {
        blobContainerClient.delete()
    }

    @Override
    ObjectStorageOperations getObjectStorage() {
        return azureBlobContainer
    }

    @Override
    Map<String, String> getProperties() {
        [
                ("${AzureBlobStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString())      : CONTAINER_NAME
        ]
    }
}
