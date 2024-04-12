package io.micronaut.objectstorage.oraclecloud

import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class OracleCloudStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<OracleCloudStorageOperations> {

    @Override
    String getConfigPrefix() {
        return OracleCloudStorageConfiguration.PREFIX
    }

    @Override
    Class<OracleCloudStorageOperations> getObjectStorage() {
        return OracleCloudStorageOperations.class
    }
}
