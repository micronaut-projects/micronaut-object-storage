package io.micronaut.objectstorage.azure

import spock.lang.Specification

import java.util.Properties

class AzureNativeImageConfigurationSpec extends Specification {

    void "it ships supplemental native image metadata for current logback releases"() {
        when:
        def stream = AzureBlobStorageOperations.classLoader.getResourceAsStream(
            'META-INF/native-image/io.micronaut.objectstorage/micronaut-object-storage-azure/native-image.properties'
        )

        then:
        stream != null

        when:
        def properties = new Properties()
        properties.load(stream)
        def args = properties.getProperty('Args')
        def initializeAtBuildTimeArgument = args?.split(/\s+/)?.find { it.startsWith('--initialize-at-build-time=') }
        def buildTimeInitializedClasses = initializeAtBuildTimeArgument == null ? null : initializeAtBuildTimeArgument
            .substring('--initialize-at-build-time='.length())
            .split(/\s*,\s*/)
            .toSet()

        then:
        args != null
        initializeAtBuildTimeArgument != null
        buildTimeInitializedClasses == [
            'ch.qos.logback.classic.model.processor.LogbackClassicDefaultNestedComponentRules',
            'ch.qos.logback.classic.util.ClassicVersionUtil',
            'ch.qos.logback.core.joran.JoranConstants',
            'ch.qos.logback.core.joran.util.PropertySetter$1',
            'ch.qos.logback.core.model.processor.ChainedModelFilter$1',
            'ch.qos.logback.core.model.processor.DefaultProcessor$1',
            'ch.qos.logback.core.model.processor.ImplicitModelHandler$1',
            'ch.qos.logback.core.subst.NodeToStringTransformer$1',
            'ch.qos.logback.core.subst.Parser$1',
            'ch.qos.logback.core.subst.Token',
            'ch.qos.logback.core.util.CoreVersionUtil',
            'ch.qos.logback.core.util.Duration',
            'org.slf4j.helpers.Reporter',
        ] as Set

        cleanup:
        stream?.close()
    }
}
