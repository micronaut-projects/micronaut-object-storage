package io.micronaut.objectstorage.azure

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Requires

@MicronautTest
@Requires({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBlobStorageCloudSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String CONTAINER_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AzureBlobStorageOperations azureBlobContainer

    @Inject
    BlobContainerClient blobContainerClient

    @Inject
    BlobServiceClient blobServiceClient

    @Override
    Map<String, String> getProperties() {
        [
                ("${AzureBlobStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString())      : CONTAINER_NAME,
                ("${AzureBlobStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.endpoint".toString())  : System.getenv("AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT")
        ]
    }

    @Override
    ObjectStorageOperations getObjectStorage() {
        return azureBlobContainer
    }

    void setup() {
        blobServiceClient.createBlobContainer(CONTAINER_NAME)
    }

    void cleanup() {
        blobContainerClient.delete()
    }
}
