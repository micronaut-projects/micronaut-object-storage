package io.micronaut.objectstorage.oraclecloud

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

@Requires({ env.ORACLE_CLOUD_TEST_NAMESPACE && env.ORACLE_CLOUD_TEST_COMPARTMENT_ID })
@MicronautTest
class OracleCloudStorageCloudSpec extends AbstractOracleCloudStorageSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.namespace'): System.getenv('ORACLE_CLOUD_TEST_NAMESPACE')]
    }
}
