package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class LocalStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<LocalStorageOperations> {

    @Override
    String getConfigPrefix() {
        return LocalStorageConfiguration.PREFIX
    }

    @Override
    Class<LocalStorageOperations> getObjectStorage() {
        return LocalStorageOperations.class
    }
}
