package io.micronaut.objectstorage.googlecloud

import com.google.cloud.storage.Storage
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification
import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.inject.qualifiers.Qualifiers

class GoogleCloudObjectStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<GoogleCloudStorageOperations> {

    @Override
    String getConfigPrefix() {
        return GoogleCloudStorageConfiguration.PREFIX
    }

    @Override
    Class<GoogleCloudStorageOperations> getObjectStorage() {
        return GoogleCloudStorageOperations.class
    }

    void "creates named reactive operations for each GCP storage configuration"() {
        given:
        def ctx = ApplicationContext.run([
                (configPrefix + '.default.bucket'): 'default-bucket',
                (configPrefix + '.other.bucket')  : 'other-bucket'
        ], Environment.TEST)
        ctx.registerSingleton(Storage, Mock(Storage))

        when:
        def defaultOperations = ctx.getBean(ReactiveObjectStorageOperations, Qualifiers.byName('default'))
        def otherOperations = ctx.getBean(ReactiveObjectStorageOperations, Qualifiers.byName('other'))

        then:
        defaultOperations
        otherOperations

        cleanup:
        ctx.close()
    }
}
