package io.micronaut.objectstorage.azure

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

@MicronautTest
@Requires({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBlobStorageCloudSpec extends AbstractAzureBlobStorageSpec {

    @Override
    Map<String, String> getProperties() {
        return super.getProperties() + [
                ("${AzureBlobStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.endpoint".toString())  : System.getenv("AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT")
        ]
    }

}
