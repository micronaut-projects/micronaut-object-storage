package io.micronaut.objectstorage.azure

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import com.azure.core.http.HttpPipeline
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import io.micronaut.context.exceptions.DisabledBeanException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.OffsetDateTime

class AzureBlobStorageFactorySpec extends Specification {

    private static final String ACCOUNT_URL = 'https://account.blob.core.windows.net'

    void 'it builds blob service clients for both credential types'() {
        given:
        def factory = new AzureBlobStorageFactory()
        def tokenConfiguration = endpointConfiguration('token-endpoint', ACCOUNT_URL, true)
        def sharedKeyConfiguration = endpointConfiguration('shared-key-endpoint', ACCOUNT_URL, true)

        when:
        def tokenBuilder = factory.blobServiceClientBuilderWithTokenCredential(tokenConfiguration, tokenCredential())
        def tokenClient = factory.blobServiceClient(tokenBuilder)
        def sharedKeyBuilder = factory.blobServiceClientBuilderWithSharedKeyCredential(sharedKeyConfiguration, sharedKeyCredential())
        def sharedKeyClient = factory.blobServiceClient(sharedKeyBuilder)

        then:
        tokenBuilder instanceof BlobServiceClientBuilder
        tokenClient.accountUrl == ACCOUNT_URL
        tokenClient.accountName == 'account'
        sharedKeyBuilder instanceof BlobServiceClientBuilder
        sharedKeyClient.accountUrl == ACCOUNT_URL
        sharedKeyClient.accountName == 'account'
    }

    void 'it builds blob container clients for both credential types'() {
        given:
        def factory = new AzureBlobStorageFactory()
        def tokenConfiguration = storageConfiguration('token-container', ACCOUNT_URL, true)
        def sharedKeyConfiguration = storageConfiguration('shared-key-container', ACCOUNT_URL, true)

        when:
        def tokenClient = factory.blobContainerClient(tokenConfiguration, tokenCredential())
        def sharedKeyClient = factory.blobContainerClient(sharedKeyConfiguration, sharedKeyCredential())

        then:
        tokenClient.accountUrl == ACCOUNT_URL
        tokenClient.blobContainerName == 'token-container'
        sharedKeyClient.accountUrl == ACCOUNT_URL
        sharedKeyClient.blobContainerName == 'shared-key-container'
    }

    void 'it rejects disabled endpoint and storage configurations'() {
        given:
        def factory = new AzureBlobStorageFactory()
        def disabledEndpointConfiguration = endpointConfiguration('disabled-endpoint', ACCOUNT_URL, false)
        def disabledStorageConfiguration = storageConfiguration('disabled-container', ACCOUNT_URL, false)

        when:
        factory.blobServiceClientBuilderWithTokenCredential(disabledEndpointConfiguration, tokenCredential())

        then:
        def e = thrown(DisabledBeanException)
        e.message == 'azure object-storage-configuration disabled-endpointis disabled'

        when:
        factory.blobServiceClientBuilderWithSharedKeyCredential(disabledEndpointConfiguration, sharedKeyCredential())

        then:
        e = thrown(DisabledBeanException)
        e.message == 'azure object-storage-configuration disabled-endpointis disabled'

        when:
        factory.blobContainerClient(disabledStorageConfiguration, tokenCredential())

        then:
        e = thrown(DisabledBeanException)
        e.message == 'azure object-storage-configuration disabled-containeris disabled'

        when:
        factory.blobContainerClient(disabledStorageConfiguration, sharedKeyCredential())

        then:
        e = thrown(DisabledBeanException)
        e.message == 'azure object-storage-configuration disabled-containeris disabled'
    }

    private static AzureBlobStorageEndpointConfiguration endpointConfiguration(String name, String endpoint, boolean enabled) {
        def configuration = new AzureBlobStorageEndpointConfiguration(name)
        configuration.endpoint = endpoint
        configuration.enabled = enabled
        configuration
    }

    private static AzureBlobStorageConfiguration storageConfiguration(String name, String endpoint, boolean enabled) {
        def configuration = new AzureBlobStorageConfiguration(name)
        configuration.endpoint = endpoint
        configuration.container = name
        configuration.enabled = enabled
        configuration
    }

    private static TokenCredential tokenCredential() {
        return new TokenCredential() {
            @Override
            Mono<AccessToken> getToken(TokenRequestContext request) {
                return Mono.just(new AccessToken('token', OffsetDateTime.now().plusHours(1)))
            }
        }
    }

    private static StorageSharedKeyCredential sharedKeyCredential() {
        return new StorageSharedKeyCredential('account', Base64.encoder.encodeToString('secret'.bytes))
    }
}
