package example.azure

import com.azure.core.http.policy.HttpPipelinePolicy
import com.azure.storage.blob.BlobServiceClientBuilder
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class BlobServiceClientBuilderCustomizer : BeanCreatedEventListener<BlobServiceClientBuilder> {

    override fun onCreated(event: BeanCreatedEvent<BlobServiceClientBuilder>): BlobServiceClientBuilder {
        val noOp = HttpPipelinePolicy { _, next -> next.process() }
        return event.bean.addPolicy(noOp)
    }
}
//end::class[]
