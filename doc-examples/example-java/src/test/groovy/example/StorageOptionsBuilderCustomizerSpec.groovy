package example

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import spock.lang.Shared
import spock.lang.Specification

class StorageOptionsBuilderCustomizerSpec extends Specification {

    public static final String SPEC_NAME = 'StorageOptionsBuilderCustomizerSpec'

    @Shared
    static GoogleCredentials credentialsMock

    void "it can customize the GCP client"() {
        given:
        credentialsMock = Mock(GoogleCredentials)
        ApplicationContext ctx = ApplicationContext.run(
                "gcp.project-id": "test-project-basdasdasd",
                "gcp.credentials.enabled": false,
                "spec.name": SPEC_NAME
        )

        when:
        StorageOptions client = ctx.getBean(StorageOptions)

        then:
        client.transportOptions.connectTimeout == 60_000

        cleanup:
        ctx.close()
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

}
