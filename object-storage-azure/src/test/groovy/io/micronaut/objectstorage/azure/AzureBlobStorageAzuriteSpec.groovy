package io.micronaut.objectstorage.azure

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

@MicronautTest
@IgnoreIf({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBlobStorageAzuriteSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String CONTAINER_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Shared
    @AutoCleanup
    GenericContainer azuriteContainer = new GenericContainer(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.18.0"))
            .withExposedPorts(10000)


    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AzureBlobStorageOperations azureBlobContainer

    @Inject
    BlobContainerClient blobContainerClient

    @Inject
    BlobServiceClient blobServiceClient

    @Override
    Map<String, String> getProperties() {
        azuriteContainer.start()
        [
                ("${AzureBlobStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString())      : CONTAINER_NAME,
                ("${AzureBlobStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.endpoint".toString())  : "http://127.0.0.1:${azuriteContainer.getMappedPort(10000)}/devstoreaccount1",
                "azure.credential.storage-shared-key.account-name"  : "devstoreaccount1",
                "azure.credential.storage-shared-key.account-key"   : "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
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
