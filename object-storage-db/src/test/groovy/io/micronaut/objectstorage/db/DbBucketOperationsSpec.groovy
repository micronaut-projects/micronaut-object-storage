package io.micronaut.objectstorage.db

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class DbBucketOperationsSpec extends BucketOperationsSpecification implements TestPropertyProvider {

    @Inject
    DbStorageOperations operations

    @Inject
    DbBucketOperations bucketOperations

    void 'deleting a bucket deletes its objects and uploads only'() {
        given:
        String other = newBucketName()
        bucketOperations.create(other)
        operations.upload(UploadRequest.fromBytes('mine'.bytes, 'kept.txt'))

        when:
        bucketOperations.create(other)

        then: 'creating an existing bucket is a no-op'
        bucketOperations.exists(other)
        bucketOperations.retrieve(other).get().nativeEntry().createdAt()

        when:
        bucketOperations.delete(other)
        bucketOperations.delete(other)

        then:
        !bucketOperations.exists(other)
        operations.exists('kept.txt')

        cleanup:
        operations.delete('kept.txt')
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        bucketOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    Map<String, String> getProperties() {
        DbTestProperties.h2()
    }
}
