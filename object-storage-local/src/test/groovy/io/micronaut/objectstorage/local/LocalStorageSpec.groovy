package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest(startApplication = true)
class LocalStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {
    @Inject
    EmbeddedServer server

    @Inject
    LocalStorageOperations operations

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return operations
    }

    @Override
    Map<String, String> getProperties() {
        return [
            (LocalStorageConfiguration.PREFIX + '.default.enabled'): "true",
            ("micronaut.object-storage.local-presigned-request-controller"): "true",
        ]
    }
}
