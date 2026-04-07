package io.micronaut.objectstorage.local

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class LocalStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<LocalStorageOperations> {

    void 'it rejects enabled as a named configuration'() {
        when:
        new LocalStorageConfiguration('enabled')

        then:
        def e = thrown(ConfigurationException)
        e.message == "The object storage configuration name 'enabled' is reserved for the module-level enabled flag"
    }

    @Override
    String getConfigPrefix() {
        return LocalStorageConfiguration.PREFIX
    }

    @Override
    Class<LocalStorageOperations> getObjectStorage() {
        return LocalStorageOperations.class
    }
}
