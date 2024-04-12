package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

abstract class AbstractAwsS3BucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    @Inject
    AwsS3BucketOperations awsBucketOperations

    @Override
    Map<String, String> getProperties() {
        [:]
    }

    @Override
    BucketOperations<?, ?, ?> getBucketOperations() {
        return awsBucketOperations
    }
}
