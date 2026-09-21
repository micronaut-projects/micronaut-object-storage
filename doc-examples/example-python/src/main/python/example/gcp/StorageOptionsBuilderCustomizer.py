from com.google.cloud.http import HttpTransportOptions
from com.google.cloud.storage import StorageOptions
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener


# tag::class[]
@Singleton
class StorageOptionsBuilderCustomizer(BeanCreatedEventListener[StorageOptions.Builder]):

    def onCreated(self, event: BeanCreatedEvent[StorageOptions.Builder]) -> StorageOptions.Builder:
        return event.getBean() \
            .setTransportOptions(HttpTransportOptions.newBuilder().setConnectTimeout(60_000).build())
# end::class[]
