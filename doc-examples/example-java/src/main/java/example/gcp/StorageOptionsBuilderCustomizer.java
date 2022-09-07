package example.gcp;

import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.StorageOptions;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

//tag::class[]
@Singleton
public class StorageOptionsBuilderCustomizer implements BeanCreatedEventListener<StorageOptions.Builder> {

    @Override
    public StorageOptions.Builder onCreated(@NonNull BeanCreatedEvent<StorageOptions.Builder> event) {
        return event.getBean()
            .setTransportOptions(HttpTransportOptions.newBuilder().setConnectTimeout(60_000).build());
    }
}
//end::class[]
