package example.gcp

import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class StorageOptionsBuilderCustomizer : BeanCreatedEventListener<StorageOptions.Builder> {

    override fun onCreated(event: BeanCreatedEvent<StorageOptions.Builder>): StorageOptions.Builder {
        return event.bean
            .setTransportOptions(HttpTransportOptions.newBuilder().setConnectTimeout(60_000).build())
    }
}
//end::class[]
