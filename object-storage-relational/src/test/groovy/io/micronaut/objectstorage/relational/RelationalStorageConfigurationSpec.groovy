package io.micronaut.objectstorage.relational

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class RelationalStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<RelationalStorageOperations> {

    void 'it rejects enabled as a named configuration'() {
        when:
        new RelationalStorageConfiguration('enabled')

        then:
        def e = thrown(ConfigurationException)
        e.message == "The object storage configuration name 'enabled' is reserved for the module-level enabled flag"
    }

    @Override
    String getConfigPrefix() {
        return RelationalStorageConfiguration.PREFIX
    }

    @Override
    Class<RelationalStorageOperations> getObjectStorage() {
        return RelationalStorageOperations.class
    }
}
