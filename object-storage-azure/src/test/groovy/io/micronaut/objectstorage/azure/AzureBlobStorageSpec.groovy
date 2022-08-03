package io.micronaut.objectstorage.azure

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Requires

@Requires({
    System.getenv("AZURE_TEST_STORAGE_CONTAINER_NAME") && System.getenv("AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT") \
    && System.getenv("AZURE_TEST_CLIENT_ID") && System.getenv("AZURE_TEST_CLIENT_SECRET") \
  && System.getenv("AZURE_TEST_TENANT_ID")
})
@MicronautTest
class AzureBlobStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    AzureBlobStorageOperations azureBlobContainer

    @Override
    Map<String, String> getProperties() {
        [
                "azure.credential.client-secret.client-id"                         : System.getenv("AZURE_TEST_CLIENT_ID"),
                "azure.credential.client-secret.secret"                            : System.getenv("AZURE_TEST_CLIENT_SECRET"),
                "azure.credential.client-secret.tenant-id"                         : System.getenv("AZURE_TEST_TENANT_ID"),
                (AzureBlobStorageConfiguration.PREFIX + ".test-container.name")    : System.getenv("AZURE_TEST_STORAGE_CONTAINER_NAME"),
                (AzureBlobStorageConfiguration.PREFIX + ".test-container.endpoint"): System.getenv("AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT")
        ]
    }

    @Override
    ObjectStorageOperations getObjectStorage() {
        return azureBlobContainer
    }
}
