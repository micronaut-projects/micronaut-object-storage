package io.micronaut.objectstorage.googlecloud

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.GrpcStorageOptions
import com.google.cloud.storage.HttpStorageOptions
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
import org.jspecify.annotations.NonNull
import spock.lang.Shared
import spock.lang.Specification

class GoogleCloudStorageFactorySpec extends Specification {

    private static final String SPEC_NAME = 'GoogleCloudStorageFactorySpec'

    @Shared
    static GoogleCredentials credentialsMock

    void "uses gRPC storage options when gRPC transport is enabled"() {
        given:
        credentialsMock = Mock(GoogleCredentials)
        ApplicationContext context = ApplicationContext.run([
                'gcp.project-id'                                            : 'test-project',
                'gcp.credentials.enabled'                                   : false,
                (GoogleCloudStorageConfiguration.PREFIX + '.grpc-enabled')  : true,
                'spec.name'                                                 : SPEC_NAME
        ])

        when:
        StorageOptions storageOptions = context.getBean(StorageOptions)

        then:
        storageOptions instanceof GrpcStorageOptions
        storageOptions.quotaProjectId == 'custom-quota-project'

        cleanup:
        context.close()
    }

    void "uses HTTP storage options by default"() {
        given:
        credentialsMock = Mock(GoogleCredentials)
        ApplicationContext context = ApplicationContext.run([
                'gcp.project-id'          : 'test-project',
                'gcp.credentials.enabled' : false,
                'spec.name'               : SPEC_NAME
        ])

        when:
        StorageOptions storageOptions = context.getBean(StorageOptions)

        then:
        storageOptions instanceof HttpStorageOptions

        cleanup:
        context.close()
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class TestCredentialsFactory {

        @Singleton
        @Primary
        GoogleCredentials googleCredentials() {
            credentialsMock
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class StorageOptionsBuilderCustomizer implements BeanCreatedEventListener<StorageOptions.Builder> {

        @Override
        StorageOptions.Builder onCreated(@NonNull BeanCreatedEvent<StorageOptions.Builder> event) {
            event.bean.setQuotaProjectId('custom-quota-project')
        }
    }
}
