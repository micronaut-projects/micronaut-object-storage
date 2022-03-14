package io.micronaut.objectstorage.azure

import io.micronaut.objectstorage.UploadObjectSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Requires

@Requires({ System.getenv("AZURE_CLIENT_ID") && System.getenv("AZURE_CLIENT_SECRET") && System.getenv("AZURE_TENANT_ID")
        && System.getenv("AZURE_CONTAINER_NAME") && System.getenv("AZURE_STORAGE_ACCOUNT_NAME")})
@MicronautTest
class AzureUploadObjectSpec extends UploadObjectSpecification implements TestPropertyProvider{

    @Override
    Map<String, String> getProperties() {
        String containerName = System.getenv("AZURE_CONTAINER_NAME")
        String storageAccountName = System.getenv("AZURE_STORAGE_ACCOUNT_NAME")

        return ["micronaut.object-storage.azure-container.${containerName}.endpoint": "https://${storageAccountName}.core.windows.net",]
    }
}
