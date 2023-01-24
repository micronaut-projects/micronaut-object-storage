package example

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import example.oraclecloud.ObjectStorageClientBuilderCustomizer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import jakarta.inject.Singleton
import spock.lang.Shared
import spock.lang.Specification

class ObjectStorageClientBuilderCustomizerSpec extends Specification {

    static final String SPEC_NAME = 'ObjectStorageClientBuilderCustomizerSpec'

    @Shared
    static AbstractAuthenticationDetailsProvider providerMock

    void "it can customise the client timeouts"() {
        given:
        providerMock = Mock(AbstractAuthenticationDetailsProvider)
        ApplicationContext ctx = ApplicationContext.run(
                ['spec.name': SPEC_NAME],
                Environment.ORACLE_CLOUD)

        when:
        ObjectStorageClient.Builder builder = ctx.getBean(ObjectStorageClient.Builder)

        then:
        builder.configuration.connectionTimeoutMillis == ObjectStorageClientBuilderCustomizer.CONNECTION_TIMEOUT_IN_MILLISECONDS
        builder.configuration.readTimeoutMillis == ObjectStorageClientBuilderCustomizer.READ_TIMEOUT_IN_MILLISECONDS

        cleanup:
        ctx.stop()
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class AuthenticationDetailsProviderFactory {

        @Singleton
        AbstractAuthenticationDetailsProvider provider() {
            providerMock
        }
    }

}
