package io.micronaut.objectstorage.azure

import com.azure.core.http.rest.Response
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlockBlobItem
import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

abstract class AbstractAzureBlobStorageSpec extends ObjectStorageOperationsSpecification<Response<BlockBlobItem>> implements TestPropertyProvider {

    public static final String CONTAINER_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

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
    ObjectStorageOperations<?, Response<BlockBlobItem>> getObjectStorage() {
        return azureBlobContainer
    }

    @Override
    @NonNull
    String eTag(Response<BlockBlobItem> blockBlobItemResponse) {
        BlockBlobItem value = blockBlobItemResponse.getValue()
        value.getETag()
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.container'): CONTAINER_NAME]
    }
}
