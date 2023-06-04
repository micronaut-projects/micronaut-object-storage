package example

import com.azure.storage.blob.BlobServiceClient
import io.micronaut.context.ApplicationContext
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

class BlobServiceClientBuilderCustomizerSpec extends Specification {

    @PendingFeature(reason = "readTimeout No longer exposed")
    void "it can customize the Azure client"() {
        given:
        ApplicationContext ctx = ApplicationContext.run((PREFIX + '.default.endpoint'): "https://127.0.0.1:10000/devstoreaccount1")

        when:
        BlobServiceClient client = ctx.getBean(BlobServiceClient)

        then:
        client.httpPipeline.httpClient.readTimeout == 30_000

        cleanup:
        ctx.stop()
    }

}
