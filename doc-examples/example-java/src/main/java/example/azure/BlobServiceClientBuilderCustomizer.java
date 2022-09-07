package example.azure;

import com.azure.core.util.HttpClientOptions;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

//tag::class[]
@Singleton
public class BlobServiceClientBuilderCustomizer implements BeanCreatedEventListener<BlobServiceClientBuilder> {

    @Override
    public BlobServiceClientBuilder onCreated(@NonNull BeanCreatedEvent<BlobServiceClientBuilder> event) {
        return event.getBean()
            .clientOptions(new HttpClientOptions().readTimeout(Duration.of(30, ChronoUnit.SECONDS)));
    }
}
//end::class[]
