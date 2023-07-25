package io.micronaut.objectstorage.azure

import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class AzureBlobStorageModuleConfigurationSpec extends ObjectStorageConfigurationSpecification<AzureBlobStorageOperations> {

    @Override
    String getConfigPrefix() {
        return AzureBlobStorageConfiguration.PREFIX
    }

    @Override
    Class<AzureBlobStorageOperations> getObjectStorage() {
        return AzureBlobStorageOperations.class
    }
}
