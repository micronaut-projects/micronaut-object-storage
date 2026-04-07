package io.micronaut.objectstorage.oraclecloud

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

@Requires({ env.ORACLE_CLOUD_TEST_NAMESPACE && env.ORACLE_CLOUD_TEST_COMPARTMENT_ID })
@MicronautTest
class OracleCloudBucketCloudSpec extends AbstractOracleCloudBucketSpec {
    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                (PREFIX + '.default.namespace'): System.getenv('ORACLE_CLOUD_TEST_NAMESPACE'),
                (PREFIX + '.default.compartment-id'): System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID')
        ]
    }
}
