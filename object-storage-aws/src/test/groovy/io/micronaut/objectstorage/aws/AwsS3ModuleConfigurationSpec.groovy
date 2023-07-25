package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

class AwsS3ModuleConfigurationSpec extends ObjectStorageConfigurationSpecification<AwsS3Operations> {

    @Override
    String getConfigPrefix() {
        return AwsS3Configuration.PREFIX
    }

    @Override
    Class<AwsS3Operations> getObjectStorage() {
        return AwsS3Operations.class
    }
}
