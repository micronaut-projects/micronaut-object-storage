package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.ObjectMetadataOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class LocalStorageObjectMetadataOperationsSpec extends ObjectMetadataOperationsSpecification implements TestPropertyProvider {

    @Inject
    LocalStorageOperations operations

    @Inject
    ObjectMetadataOperations<?> metadataOperations

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    ObjectMetadataOperations<?> getObjectMetadataOperations() {
        metadataOperations
    }

    @Override
    Map<String, String> getProperties() {
        [(LocalStorageConfiguration.PREFIX + '.default.enabled'): "true"]
    }
}
