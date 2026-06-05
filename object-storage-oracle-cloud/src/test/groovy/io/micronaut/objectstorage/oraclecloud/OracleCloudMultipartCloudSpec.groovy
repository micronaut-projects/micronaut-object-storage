package io.micronaut.objectstorage.oraclecloud

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.IgnoreIf
import spock.lang.Requires

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

@Requires({ env.ORACLE_CLOUD_TEST_NAMESPACE && env.ORACLE_CLOUD_TEST_COMPARTMENT_ID })
@IgnoreIf({ !env.ORACLE_CLOUD_TEST_NAMESPACE || !env.ORACLE_CLOUD_TEST_COMPARTMENT_ID || !System.getenv('OCI_SESSION_TOKEN') })
@MicronautTest
class OracleCloudMultipartCloudSpec extends AbstractOracleCloudMultipartSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
            (PREFIX + '.default.namespace'): System.getenv('ORACLE_CLOUD_TEST_NAMESPACE'),
            (PREFIX + '.default.compartment-id'): System.getenv('ORACLE_CLOUD_TEST_COMPARTMENT_ID'),
            'micronaut.server.max-request-buffer-size': (nonFinalMultipartPartSizeBytes() + 1024 * 1024).toString()
        ]
    }
}
