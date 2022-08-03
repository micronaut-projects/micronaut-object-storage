package io.micronaut.objectstorage.azure

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class AzureBlobStorageConfigurationSpec extends Specification {

    def "it configures the configuration to use parameter as name"() {
        given:
        def applicationContext = ApplicationContext.run(
                [
                        (AzureBlobStorageConfiguration.PREFIX + ".test-container.endpoint"): "endpoint"
                ])

        when:
        def configuration = applicationContext.getBean(AzureBlobStorageConfiguration)

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
                        (AzureBlobStorageConfiguration.PREFIX + ".test-container.name")    : "custom-container-name",
                        (AzureBlobStorageConfiguration.PREFIX + ".test-container.endpoint"): "endpoint"
                ])

        when:
        def configuration = applicationContext.getBean(AzureBlobStorageConfiguration)

        then:
        configuration
        configuration.name == "custom-container-name"
        configuration.endpoint == "endpoint"

        cleanup:
        applicationContext.close()
    }

}
