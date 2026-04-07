package io.micronaut.objectstorage.azure

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DisabledBeanException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.exceptions.NoSuchBeanException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.OffsetDateTime

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

class AzureBlobStorageFactorySpec extends Specification {

    private static final String ACCOUNT_URL = 'https://account.blob.core.windows.net'

    void 'it builds blob service clients for both credential types'() {
        given:
        def factory = new AzureBlobStorageFactory(Mock(io.micronaut.context.BeanContext))
        def tokenConfiguration = storageConfiguration('token-endpoint', ACCOUNT_URL, true)
        def sharedKeyConfiguration = storageConfiguration('shared-key-endpoint', ACCOUNT_URL, true)

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
        def beanContext = Mock(io.micronaut.context.BeanContext)
        def factory = new AzureBlobStorageFactory(beanContext)
        def tokenConfiguration = storageConfiguration('token-container', ACCOUNT_URL, true)
        def sharedKeyConfiguration = storageConfiguration('shared-key-container', ACCOUNT_URL, true)
        def tokenClient = factory.blobServiceClient(factory.blobServiceClientBuilderWithTokenCredential(tokenConfiguration, tokenCredential()))
        def sharedKeyClient = factory.blobServiceClient(factory.blobServiceClientBuilderWithSharedKeyCredential(sharedKeyConfiguration, sharedKeyCredential()))
        def tokenAsyncClient = factory.blobServiceAsyncClient(factory.blobServiceClientBuilderWithTokenCredential(tokenConfiguration, tokenCredential()))
        def sharedKeyAsyncClient = factory.blobServiceAsyncClient(factory.blobServiceClientBuilderWithSharedKeyCredential(sharedKeyConfiguration, sharedKeyCredential()))

        when:
        def tokenContainerClient = factory.blobContainerClient('token-container', tokenClient)
        def sharedKeyContainerClient = factory.blobContainerClient('shared-key-container', sharedKeyClient)
        def tokenContainerAsyncClient = factory.blobContainerAsyncClient('token-container', tokenAsyncClient)
        def sharedKeyContainerAsyncClient = factory.blobContainerAsyncClient('shared-key-container', sharedKeyAsyncClient)

        then:
        1 * beanContext.getBean(AzureBlobStorageConfiguration, _ as io.micronaut.context.Qualifier) >> tokenConfiguration
        1 * beanContext.getBean(AzureBlobStorageConfiguration, _ as io.micronaut.context.Qualifier) >> sharedKeyConfiguration
        1 * beanContext.getBean(AzureBlobStorageConfiguration, _ as io.micronaut.context.Qualifier) >> tokenConfiguration
        1 * beanContext.getBean(AzureBlobStorageConfiguration, _ as io.micronaut.context.Qualifier) >> sharedKeyConfiguration
        tokenContainerClient.accountUrl == ACCOUNT_URL
        tokenContainerClient.blobContainerName == 'token-container'
        sharedKeyContainerClient.accountUrl == ACCOUNT_URL
        sharedKeyContainerClient.blobContainerName == 'shared-key-container'
        tokenContainerAsyncClient.accountUrl == ACCOUNT_URL
        tokenContainerAsyncClient.blobContainerName == 'token-container'
        sharedKeyContainerAsyncClient.accountUrl == ACCOUNT_URL
        sharedKeyContainerAsyncClient.blobContainerName == 'shared-key-container'
    }

    void 'it rejects disabled endpoint and storage configurations'() {
        given:
        def factory = new AzureBlobStorageFactory(Mock(io.micronaut.context.BeanContext))
        def disabledConfiguration = storageConfiguration('disabled-endpoint', ACCOUNT_URL, false)

        when:
        factory.blobServiceClientBuilderWithTokenCredential(disabledConfiguration, tokenCredential())

        then:
        def e = thrown(DisabledBeanException)
        e.message == 'azure object-storage-configuration disabled-endpoint is disabled'

        when:
        factory.blobServiceClientBuilderWithSharedKeyCredential(disabledConfiguration, sharedKeyCredential())

        then:
        e = thrown(DisabledBeanException)
        e.message == 'azure object-storage-configuration disabled-endpoint is disabled'
    }

    void "it creates Azure service and container beans with shared-key credentials"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            (PREFIX + '.default.endpoint')                     : 'https://example.blob.core.windows.net',
            (PREFIX + '.default.container')                    : 'images',
            'azure.credential.storage-shared-key.account-name': 'devstoreaccount1',
            'azure.credential.storage-shared-key.account-key' : 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==',
        ])

        expect:
        applicationContext.getBean(BlobServiceClientBuilder)
        applicationContext.getBean(BlobServiceClient)
        applicationContext.getBean(BlobServiceAsyncClient)
        applicationContext.getBean(BlobContainerClient)
        applicationContext.getBean(BlobContainerAsyncClient)

        cleanup:
        applicationContext.close()
    }

    void "it creates Azure service and container beans with token credentials"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder()
            .properties([
                (PREFIX + '.default.endpoint') : 'https://example.blob.core.windows.net',
                (PREFIX + '.default.container'): 'images',
            ])
            .singletons(new TestTokenCredential())
            .start()

        expect:
        applicationContext.getBean(BlobServiceClientBuilder)
        applicationContext.getBean(BlobServiceClient)
        applicationContext.getBean(BlobServiceAsyncClient)
        applicationContext.getBean(BlobContainerClient)
        applicationContext.getBean(BlobContainerAsyncClient)

        cleanup:
        applicationContext.close()
    }

    void "it rejects disabled Azure configurations"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            (PREFIX + '.default.enabled')                     : false,
            (PREFIX + '.default.endpoint')                    : 'https://example.blob.core.windows.net',
            (PREFIX + '.default.container')                   : 'images',
            'azure.credential.storage-shared-key.account-name': 'devstoreaccount1',
            'azure.credential.storage-shared-key.account-key' : 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==',
        ])

        when:
        applicationContext.getBean(BlobServiceClientBuilder)

        then:
        def error = thrown(NoSuchBeanException)
        error.message.contains('disabled')

        cleanup:
        applicationContext.close()
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

    @Primary
    private static final class TestTokenCredential implements TokenCredential {
        @Override
        Mono<AccessToken> getToken(TokenRequestContext request) {
            Mono.just(new AccessToken('token', OffsetDateTime.now().plusMinutes(30)))
        }
    }
}
