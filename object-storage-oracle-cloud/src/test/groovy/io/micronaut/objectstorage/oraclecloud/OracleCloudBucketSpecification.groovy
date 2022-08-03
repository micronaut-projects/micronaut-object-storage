package io.micronaut.objectstorage.oraclecloud

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Requires

@Requires({ System.getenv("ORACLE_CLOUD_TEST_BUCKET_NAME") && System.getenv("ORACLE_CLOUD_TEST_NAMESPACE") })
@MicronautTest
class OracleCloudBucketSpecification extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    OracleCloudBucket oracleCloudBucket


    @Override
    Map<String, String> getProperties() {
        [
                (OracleCloudBucketConfiguration.PREFIX + ".test-bucket.name")       : System.getenv("ORACLE_CLOUD_TEST_BUCKET_NAME"),
                (OracleCloudBucketConfiguration.PREFIX + ".test-bucket.namespace")  : System.getenv("ORACLE_CLOUD_TEST_NAMESPACE"),
        ]
    }

    ObjectStorageOperations getObjectStorage() {
        return oracleCloudBucket
    }

}
