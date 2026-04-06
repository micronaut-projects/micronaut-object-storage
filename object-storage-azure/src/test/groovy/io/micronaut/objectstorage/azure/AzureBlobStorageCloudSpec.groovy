package io.micronaut.objectstorage.azure

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

@MicronautTest
@Requires({
    String endpoint = env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT
    String clientId = env.AZURE_CLIENT_ID
    String clientSecret = env.AZURE_CLIENT_SECRET
    String tenantId = env.AZURE_TENANT_ID
    return endpoint?.startsWith('https://') && clientId && clientSecret && tenantId && !Boolean.getBoolean('azure.test.skip.cloud')
})
class AzureBlobStorageCloudSpec extends AbstractAzureBlobStorageSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                (PREFIX + '.' + OBJECT_STORAGE_NAME + '.endpoint'): System.getenv('AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT')]
    }

    @Override
    boolean supportsPresignedUploadRoundTrip() {
        true
    }
}
