package io.micronaut.objectstorage.googlecloud

import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class GoogleCloudObjectStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<GoogleCloudStorageOperations> {

    @Override
    String getConfigPrefix() {
        return GoogleCloudStorageConfiguration.PREFIX
    }

    @Override
    Class<GoogleCloudStorageOperations> getObjectStorage() {
        return GoogleCloudStorageOperations.class
    }
}
