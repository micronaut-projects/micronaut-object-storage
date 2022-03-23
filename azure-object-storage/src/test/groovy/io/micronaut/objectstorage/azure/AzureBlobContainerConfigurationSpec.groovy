package io.micronaut.objectstorage.azure

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class AzureBlobContainerConfigurationSpec extends Specification {

    def "it configures the configuration to use parameter as name"() {
        given:
        def applicationContext = ApplicationContext.run(
                [
                        "micronaut.object-storage.azure-container.test-container.endpoint": "endpoint"
                ])

        when:
        def configuration = applicationContext.getBean(AzureBlobContainerConfiguration)

        then:
        configuration
        configuration.name == "test-container"
        configuration.endpoint == "endpoint"

        cleanup:
        applicationContext.close()
    }

    def "it configures the custom name of container"() {
        given:
        def applicationContext = ApplicationContext.run(
                [
                        "micronaut.object-storage.azure-container.test-container.name"    : "custom-container-name",
                        "micronaut.object-storage.azure-container.test-container.endpoint": "endpoint"
                ])

        when:
        def configuration = applicationContext.getBean(AzureBlobContainerConfiguration)

        then:
        configuration
        configuration.name == "custom-container-name"
        configuration.endpoint == "endpoint"

        cleanup:
        applicationContext.close()
    }

}
