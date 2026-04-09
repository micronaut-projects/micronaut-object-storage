package io.micronaut.objectstorage.relational

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import jakarta.inject.Inject

abstract class AbstractRelationalStorageOperationsSpec extends ObjectStorageOperationsSpecification {

    @Inject
    RelationalStorageOperations operations

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return operations
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return null
    }
}
