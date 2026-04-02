package io.micronaut.objectstorage.azure

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Primary
import io.micronaut.context.exceptions.NoSuchBeanException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.OffsetDateTime

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

class AzureBlobStorageFactorySpec extends Specification {

    void "it creates Azure service and container beans with shared-key credentials"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            (PREFIX + '.default.endpoint')                            : 'https://example.blob.core.windows.net',
            (PREFIX + '.default.container')                           : 'images',
            'azure.credential.storage-shared-key.account-name'        : 'devstoreaccount1',
            'azure.credential.storage-shared-key.account-key'         : 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==',
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
                (PREFIX + '.default.endpoint')  : 'https://example.blob.core.windows.net',
                (PREFIX + '.default.container') : 'images',
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
            (PREFIX + '.default.enabled')                             : false,
            (PREFIX + '.default.endpoint')                            : 'https://example.blob.core.windows.net',
            (PREFIX + '.default.container')                           : 'images',
            'azure.credential.storage-shared-key.account-name'        : 'devstoreaccount1',
            'azure.credential.storage-shared-key.account-key'         : 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==',
        ])

        when:
        applicationContext.getBean(BlobServiceClientBuilder)

        then:
        def error = thrown(NoSuchBeanException)
        error.message.contains('disabled')

        cleanup:
        applicationContext.close()
    }

    @Primary
    private static final class TestTokenCredential implements TokenCredential {
        @Override
        Mono<AccessToken> getToken(com.azure.core.credential.TokenRequestContext request) {
            Mono.just(new AccessToken('token', OffsetDateTime.now().plusMinutes(30)))
        }
    }
}
