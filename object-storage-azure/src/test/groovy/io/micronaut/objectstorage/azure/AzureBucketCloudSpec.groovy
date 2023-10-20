package io.micronaut.objectstorage.azure

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

@MicronautTest
@Requires({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBucketCloudSpec extends AbstractAzureBucketSpec {
    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [(AzureBlobStorageEndpointConfiguration.PREFIX + '.default.endpoint'): System.getenv('AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT')]
    }
}
