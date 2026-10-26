package io.micronaut.objectstorage.db.suite

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.db.DbBucketOperations
import io.micronaut.objectstorage.db.DbStorageOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class SuiteChunkedStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

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
        SuiteProperties.storage('chunked', ['content-layout': 'CHUNKED', 'chunk-size': '16'])
    }
}
