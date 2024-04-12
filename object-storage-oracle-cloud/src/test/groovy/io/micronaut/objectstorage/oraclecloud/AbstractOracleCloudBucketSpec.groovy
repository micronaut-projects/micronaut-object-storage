package io.micronaut.objectstorage.oraclecloud

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

abstract class AbstractOracleCloudBucketSpec extends BucketOperationsSpecification implements TestPropertyProvider {
    @Inject
    OracleCloudBucketOperations oracleCloudBucketOperations

    @Override
    Map<String, String> getProperties() {
        [(OracleCloudNamespaceConfiguration.PREFIX + '.default.enabled'): "true"]
    }

    @Override
    BucketOperations<?, ?, ?> getBucketOperations() {
        return oracleCloudBucketOperations
    }
}
