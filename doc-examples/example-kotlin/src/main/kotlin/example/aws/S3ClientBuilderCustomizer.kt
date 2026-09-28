package example.aws

import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.services.s3.S3ClientBuilder
import java.time.Duration
import java.time.temporal.ChronoUnit

//tag::class[]
@Singleton
class S3ClientBuilderCustomizer : BeanCreatedEventListener<S3ClientBuilder> {

    override fun onCreated(event: BeanCreatedEvent<S3ClientBuilder>): S3ClientBuilder {
        return event.bean
            .overrideConfiguration { c: ClientOverrideConfiguration.Builder -> c.apiCallTimeout(Duration.of(60, ChronoUnit.SECONDS)) }
    }
}
//end::class[]
