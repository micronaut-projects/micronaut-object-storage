package example.azure;

import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

//tag::class[]
@Singleton
public class BlobServiceClientBuilderCustomizer implements BeanCreatedEventListener<BlobServiceClientBuilder> {

    @Override
    public BlobServiceClientBuilder onCreated(@NonNull BeanCreatedEvent<BlobServiceClientBuilder> event) {
        HttpPipelinePolicy noOp = (context, next) -> next.process();
        return event.getBean().addPolicy(noOp);
    }
}
//end::class[]
