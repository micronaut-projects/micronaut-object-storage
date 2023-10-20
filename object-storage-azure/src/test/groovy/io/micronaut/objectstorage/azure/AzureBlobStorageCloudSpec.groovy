package io.micronaut.objectstorage.azure

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

@MicronautTest
@Requires({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBlobStorageCloudSpec extends AbstractAzureBlobStorageSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                // AbstractAzureBlobStorageSpec needs to create a container
                (AzureBlobStorageEndpointConfiguration.PREFIX + '.default.endpoint'): System.getenv('AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT'),
                (PREFIX + '.' + OBJECT_STORAGE_NAME + '.endpoint'): System.getenv('AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT')]
    }
}
