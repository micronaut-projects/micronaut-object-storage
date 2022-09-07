package example.aws;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

//tag::class[]
@Singleton
public class S3ClientBuilderCustomizer implements BeanCreatedEventListener<S3ClientBuilder> {

    @Override
    public S3ClientBuilder onCreated(@NonNull BeanCreatedEvent<S3ClientBuilder> event) {
        return event.getBean()
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.of(60, ChronoUnit.SECONDS)));
    }
}
//end::class[]
