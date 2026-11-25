package io.micronaut.objectstorage.db

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class DbStorageSingleColumnSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    DbStorageOperations operations

    @Inject
    DbBucketOperations bucketOperations

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        bucketOperations
    }

    @Override
    Map<String, String> getProperties() {
        // tiny chunks so that every object spans several windows
        DbTestProperties.h2('content-layout': 'SINGLE_COLUMN', 'chunk-size': '4')
    }
}
