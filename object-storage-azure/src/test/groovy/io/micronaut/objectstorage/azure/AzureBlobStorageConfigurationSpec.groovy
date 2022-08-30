package io.micronaut.objectstorage.azure

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

class AzureBlobStorageConfigurationSpec extends Specification {

    void 'it configures the configuration to use parameter as name'() {
        given:
        def applicationContext = ApplicationContext.run((PREFIX + '.test-container.endpoint'): 'endpoint')

        when:
        def configuration = applicationContext.getBean(AzureBlobStorageConfiguration)

        then:
        configuration
        configuration.name == 'test-container'
        configuration.endpoint == 'endpoint'

        cleanup:
        applicationContext.close()
    }

    void 'it configures the custom name of container'() {
        given:
        def applicationContext = ApplicationContext.run(
                [
                        (PREFIX + '.test-container.name')    : 'custom-container-name',
                        (PREFIX + '.test-container.endpoint'): 'endpoint'
                ])

        when:
        def configuration = applicationContext.getBean(AzureBlobStorageConfiguration)

        then:
        configuration
        configuration.name == 'custom-container-name'
        configuration.endpoint == 'endpoint'

        cleanup:
        applicationContext.close()
    }
}
