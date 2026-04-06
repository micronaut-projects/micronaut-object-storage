package io.micronaut.objectstorage.oraclecloud

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.IgnoreIf
import spock.lang.Requires

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

@Requires({ env.ORACLE_CLOUD_TEST_NAMESPACE && env.ORACLE_CLOUD_TEST_COMPARTMENT_ID })
@IgnoreIf({ !env.ORACLE_CLOUD_TEST_NAMESPACE || !env.ORACLE_CLOUD_TEST_COMPARTMENT_ID || !System.getenv('OCI_SESSION_TOKEN') })
@MicronautTest
@Property(name = 'spec.name', value = SPEC_NAME)
class OracleCloudStorageCloudSpec extends AbstractOracleCloudStorageSpec {

    public static final String SPEC_NAME = 'OracleCloudStorageCloudSpec'

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.namespace'): System.getenv('ORACLE_CLOUD_TEST_NAMESPACE')]
    }

    @Override
    boolean supportsPresignedUploadRoundTrip() {
        true
    }
}
