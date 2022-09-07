package example

import com.google.cloud.storage.StorageOptions
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class StorageOptionsBuilderCustomizerSpec extends Specification {

    void "it can customize the GCP client"() {
        given:
        ApplicationContext ctx = ApplicationContext.run("gcp.project-id": "test-project")

        when:
        StorageOptions client = ctx.getBean(StorageOptions)

        then:
        client.transportOptions.connectTimeout == 60_000

        cleanup:
        ctx.close()
    }

}
