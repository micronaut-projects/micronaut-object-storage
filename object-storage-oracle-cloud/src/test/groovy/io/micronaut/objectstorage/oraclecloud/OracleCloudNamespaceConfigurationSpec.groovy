package io.micronaut.objectstorage.oraclecloud

import spock.lang.Specification

class OracleCloudNamespaceConfigurationSpec extends Specification {

    void 'it stores namespace configuration properties'() {
        given:
        def configuration = new OracleCloudNamespaceConfiguration()

        expect:
        configuration.enabled

        when:
        configuration.enabled = false
        configuration.compartmentId = 'compartment'
        configuration.namespace = 'namespace'

        then:
        !configuration.enabled
        configuration.compartmentId == 'compartment'
        configuration.namespace == 'namespace'
    }
}
