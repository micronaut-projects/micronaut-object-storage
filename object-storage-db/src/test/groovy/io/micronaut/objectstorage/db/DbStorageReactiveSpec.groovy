package io.micronaut.objectstorage.db

import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class DbStorageReactiveSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    ReactiveObjectStorageOperations<?, ?, ?> operations

    @Override
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    Map<String, String> getProperties() {
        DbTestProperties.h2()
    }
}
