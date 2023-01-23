package example

import com.oracle.bmc.objectstorage.ObjectStorageClient
import example.oraclecloud.ObjectStorageClientBuilderCustomizer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification

class ObjectStorageClientBuilderCustomizerSpec extends Specification {

    void "it can customise the client timeouts"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(Environment.ORACLE_CLOUD)

        when:
        ObjectStorageClient.Builder builder = ctx.getBean(ObjectStorageClient.Builder)

        then:
        builder.configuration.connectionTimeoutMillis == ObjectStorageClientBuilderCustomizer.CONNECTION_TIMEOUT_IN_MILLISECONDS
        builder.configuration.readTimeoutMillis == ObjectStorageClientBuilderCustomizer.READ_TIMEOUT_IN_MILLISECONDS

        cleanup:
        ctx.stop()
    }

}
