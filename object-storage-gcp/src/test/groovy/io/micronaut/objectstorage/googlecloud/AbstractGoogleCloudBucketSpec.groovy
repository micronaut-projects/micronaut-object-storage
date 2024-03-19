package io.micronaut.objectstorage.googlecloud

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

abstract class AbstractGoogleCloudBucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    @Inject
    GoogleCloudBucketOperations googleCloudBucketOperations

    @Override
    Map<String, String> getProperties() {
        [:]
    }

    @Override
    BucketOperations<?, ?, ?> getBucketOperations() {
        return googleCloudBucketOperations
    }
}
