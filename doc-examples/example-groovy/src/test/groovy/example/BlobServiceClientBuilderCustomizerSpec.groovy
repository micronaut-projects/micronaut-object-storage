package example

import com.azure.storage.blob.BlobServiceClient
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import java.lang.reflect.Proxy

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

class BlobServiceClientBuilderCustomizerSpec extends Specification {

    void "it can customize the Azure client"() {
        given:
        ApplicationContext ctx = ApplicationContext.run((PREFIX + '.default.endpoint'): "https://127.0.0.1:10000/devstoreaccount1")

        when:
        BlobServiceClient client = ctx.getBean(BlobServiceClient)

        then: 'the no-op policy of the customizer, a closure coerced to HttpPipelinePolicy, is in the pipeline'
        client.httpPipeline.pipelinePolicies.find { Proxy.isProxyClass(it.class) }

        cleanup:
        ctx.stop()
    }

}
