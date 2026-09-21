package example.gcp

import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class StorageOptionsBuilderCustomizer implements BeanCreatedEventListener<StorageOptions.Builder> {

    @Override
    StorageOptions.Builder onCreated(BeanCreatedEvent<StorageOptions.Builder> event) {
        event.bean
            .setTransportOptions(HttpTransportOptions.newBuilder().setConnectTimeout(60_000).build())
    }
}
//end::class[]
