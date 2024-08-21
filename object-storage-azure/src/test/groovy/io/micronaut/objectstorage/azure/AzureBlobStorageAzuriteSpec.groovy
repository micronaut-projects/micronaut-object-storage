package io.micronaut.objectstorage.azure

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

@MicronautTest
@IgnoreIf({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBlobStorageAzuriteSpec extends AbstractAzureBlobStorageSpec {

    @Shared
    @AutoCleanup
    GenericContainer azuriteContainer = new GenericContainer(DockerImageName.parse('mcr.microsoft.com/azure-storage/azurite:3.32.0'))
            .withExposedPorts(10000)

    @Override
    Map<String, String> getProperties() {
        azuriteContainer.start()
        super.getProperties() + [
                (PREFIX + '.' + OBJECT_STORAGE_NAME + '.endpoint'): "http://127.0.0.1:${azuriteContainer.getMappedPort(10000)}/devstoreaccount1",
                'azure.credential.storage-shared-key.account-name': 'devstoreaccount1',
                'azure.credential.storage-shared-key.account-key' : 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=='
        ]
    }
}
