package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class LocalStorageReactiveSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    ReactiveObjectStorageOperations<?, ?, ?> operations

    @Override
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return operations
    }

    @Override
    Map<String, String> getProperties() {
        [(LocalStorageConfiguration.PREFIX + '.default.enabled'): "true"]
    }
}
