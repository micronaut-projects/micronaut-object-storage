package example

import com.azure.storage.blob.BlobServiceClient
import example.azure.BlobServiceClientBuilderCustomizer
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

class BlobServiceClientBuilderCustomizerSpec extends Specification {

    void "it can customize the Azure client"() {
        given:
        ApplicationContext ctx = ApplicationContext.run((PREFIX + '.default.endpoint'): "https://127.0.0.1:10000/devstoreaccount1")

        when:
        BlobServiceClient client = ctx.getBean(BlobServiceClient)

        then:
        client.httpPipeline.pipelinePolicies.find { it.class.simpleName.startsWith BlobServiceClientBuilderCustomizer.class.simpleName }

        cleanup:
        ctx.stop()
    }

}
