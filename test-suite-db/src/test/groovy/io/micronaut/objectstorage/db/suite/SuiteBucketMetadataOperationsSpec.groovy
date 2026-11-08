package io.micronaut.objectstorage.db.suite

import io.micronaut.objectstorage.db.*

import io.micronaut.objectstorage.BucketMetadataOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class SuiteBucketMetadataOperationsSpec extends BucketMetadataOperationsSpecification implements TestPropertyProvider {

    @Inject
    DbStorageOperations operations

    @Inject
    DbBucketOperations bucketOperations

    @Inject
    DbBucketMetadataOperations bucketMetadataOperations

    void 'saving metadata of a missing bucket fails'() {
        when:
        bucketMetadataOperations.save(new BucketMetadataWrite('missing-bucket', [a: 'b']))

        then:
        thrown(ObjectStorageException)
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        bucketOperations
    }

    @Override
    BucketMetadataOperations<?> getBucketMetadataOperations() {
        bucketMetadataOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    Map<String, String> getProperties() {
        SuiteProperties.storage('bucketmetadata')
    }
}
