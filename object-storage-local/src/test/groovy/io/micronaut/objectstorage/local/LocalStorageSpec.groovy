package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class LocalStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    LocalStorageOperations operations

    @Inject
    LocalStorageBucketOperations bucketOperations

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return operations
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return bucketOperations
    }

    @Override
    Map<String, String> getProperties() {
        [(LocalStorageConfiguration.PREFIX + '.default.enabled'): "true"]
    }
}
