package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class LocalStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    LocalStorageOperations operations

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return operations
    }

    @Override
    Map<String, String> getProperties() {
        [(LocalStorageConfiguration.PREFIX + '.default.enabled'): "true"]
    }
}
