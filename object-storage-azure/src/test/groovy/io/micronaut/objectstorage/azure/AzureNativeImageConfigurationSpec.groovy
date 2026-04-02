package io.micronaut.objectstorage.azure

import spock.lang.Specification

class AzureNativeImageConfigurationSpec extends Specification {

    void "it ships supplemental native image metadata for current logback releases"() {
        when:
        def stream = AzureBlobStorageOperations.classLoader.getResourceAsStream(
            'META-INF/native-image/io.micronaut.objectstorage/micronaut-object-storage-azure/native-image.properties'
        )

        then:
        stream != null

        when:
        def properties = stream.text

        then:
        properties.contains('ch.qos.logback.classic.model.processor.LogbackClassicDefaultNestedComponentRules')
        properties.contains('ch.qos.logback.classic.util.ClassicVersionUtil')
        properties.contains('ch.qos.logback.core.joran.JoranConstants')
        properties.contains('ch.qos.logback.core.joran.util.PropertySetter$1')
        properties.contains('ch.qos.logback.core.model.processor.ChainedModelFilter$1')
        properties.contains('ch.qos.logback.core.model.processor.DefaultProcessor$1')
        properties.contains('ch.qos.logback.core.model.processor.ImplicitModelHandler$1')
        properties.contains('ch.qos.logback.core.subst.NodeToStringTransformer$1')
        properties.contains('ch.qos.logback.core.subst.Parser$1')
        properties.contains('ch.qos.logback.core.subst.Token')
        properties.contains('ch.qos.logback.core.util.CoreVersionUtil')
        properties.contains('ch.qos.logback.core.util.Duration')
        properties.contains('org.slf4j.helpers.Reporter')

        cleanup:
        stream?.close()
    }
}
