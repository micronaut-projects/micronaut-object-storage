package io.micronaut.objectstorage.azure

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

abstract class AbstractAzureBucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    @Inject
    AzureBlobBucketOperations azureBucketOperations

    @Override
    Map<String, String> getProperties() {
        [:]
    }

    @Override
    BucketOperations<?, ?, ?> getBucketOperations() {
        return azureBucketOperations
    }
}
