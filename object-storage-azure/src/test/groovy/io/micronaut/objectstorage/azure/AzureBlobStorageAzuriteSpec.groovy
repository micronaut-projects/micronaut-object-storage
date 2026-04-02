package io.micronaut.objectstorage.azure

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Shared

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

@Ignore("The API version 2026-02-06 is not supported by Azurite. Please upgrade Azurite to latest version and retry.")
@MicronautTest
@IgnoreIf({ env.AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET && env.AZURE_TENANT_ID })
class AzureBlobStorageAzuriteSpec extends AbstractAzureBlobStorageSpec {

    private static final DockerImageName AZURITE_IMAGE = DockerImageName.parse('mcr.microsoft.com/azure-storage/azurite:3.35.0@sha256:647c63a91102a9d8e8000aab803436e1fc85fbb285e7ce830a82ee5d6661cf37')

    @Shared
    @AutoCleanup
    GenericContainer azuriteContainer = new GenericContainer(AZURITE_IMAGE)
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")
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
