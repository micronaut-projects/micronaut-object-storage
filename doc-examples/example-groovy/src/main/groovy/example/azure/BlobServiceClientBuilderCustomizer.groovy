package example.azure

import com.azure.core.http.policy.HttpPipelinePolicy
import com.azure.storage.blob.BlobServiceClientBuilder
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class BlobServiceClientBuilderCustomizer implements BeanCreatedEventListener<BlobServiceClientBuilder> {

    @Override
    BlobServiceClientBuilder onCreated(BeanCreatedEvent<BlobServiceClientBuilder> event) {
        HttpPipelinePolicy noOp = { context, next -> next.process() }
        event.bean.addPolicy(noOp)
    }
}
//end::class[]
