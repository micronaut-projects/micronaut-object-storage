package example.aws

import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3ClientBuilder

import java.time.Duration
import java.time.temporal.ChronoUnit

//tag::class[]
@Singleton
class S3ClientBuilderCustomizer implements BeanCreatedEventListener<S3ClientBuilder> {

    @Override
    S3ClientBuilder onCreated(BeanCreatedEvent<S3ClientBuilder> event) {
        event.bean
            .overrideConfiguration { c -> c.apiCallTimeout(Duration.of(60, ChronoUnit.SECONDS)) }
    }
}
//end::class[]
