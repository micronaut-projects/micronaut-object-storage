from jakarta.inject import Singleton
from java.time import Duration
from java.time.temporal import ChronoUnit
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from software.amazon.awssdk.services.s3 import S3ClientBuilder


# tag::class[]
@Singleton
class S3ClientBuilderCustomizer(BeanCreatedEventListener[S3ClientBuilder]):

    def onCreated(self, event: BeanCreatedEvent[S3ClientBuilder]) -> S3ClientBuilder:
        return event.getBean() \
            .overrideConfiguration(lambda c: c.apiCallTimeout(Duration.of(60, ChronoUnit.SECONDS)))
# end::class[]
