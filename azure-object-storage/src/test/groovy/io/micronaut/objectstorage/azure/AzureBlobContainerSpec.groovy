package io.micronaut.objectstorage.azure

import com.azure.core.credential.TokenCredential
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorage
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification
import spock.lang.Unroll

@MicronautTest
class AzureBlobContainerSpec extends Specification implements TestPropertyProvider {


    Map<String, String> getProperties() {
        [
                "micronaut.object-storage.azure-container.container-a.endpoint": "https://container-a.blob.core.windows.net",
                "micronaut.object-storage.azure-container.container-b.endpoint": "https://container-b.blob.core.windows.net",
                "micronaut.object-storage.azure-container.container-c.endpoint": "https://container-c.blob.core.windows.net"
        ]
    }

    @Inject
    ApplicationContext applicationContext

    @Inject
    @Named("container-a")
    ObjectStorage azureBlobContainerA

    @Inject
    @Named("container-b")
    ObjectStorage azureBlobContainerB

    @Inject
    @Named("container-c")
    ObjectStorage azureBlobContainerC


    def "it was properly injected using @Named"() {
        expect:
        azureBlobContainerA
        azureBlobContainerB
        azureBlobContainerC
    }


        @Unroll
    def "it can be accessed by name=#name"(String name) {
        when:
        def bean = applicationContext.getBean(AzureBlobContainer, Qualifiers.byName(name))

        then:
        bean
        bean.configuration.name == name
        bean.configuration.endpoint == "https://${name}.blob.core.windows.net"

        where:
        name << ["container-a", "container-b", "container-c"]
    }

    @MockBean
    TokenCredential tokenCredential() {
        return Mock(TokenCredential)
    }

}
