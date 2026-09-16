from com.azure.core.http.policy import HttpPipelinePolicy
from com.azure.storage.blob import BlobServiceClientBuilder
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener


# tag::class[]
@Singleton
class BlobServiceClientBuilderCustomizer(BeanCreatedEventListener[BlobServiceClientBuilder]):

    def onCreated(self, event: BeanCreatedEvent[BlobServiceClientBuilder]) -> BlobServiceClientBuilder:
        no_op: HttpPipelinePolicy = lambda context, next: next.process()
        return event.getBean().addPolicy(no_op)
# end::class[]
