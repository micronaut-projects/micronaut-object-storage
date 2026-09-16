from com.oracle.bmc import ClientConfiguration
from com.oracle.bmc.objectstorage import ObjectStorageClient
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener


# tag::class[]
# See https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ClientConfigurationTimeoutExample.java
@Singleton
class ObjectStorageClientBuilderCustomizer(BeanCreatedEventListener[ObjectStorageClient.Builder]):

    CONNECTION_TIMEOUT_IN_MILLISECONDS: int = 25000
    READ_TIMEOUT_IN_MILLISECONDS: int = 35000

    def onCreated(self, event: BeanCreatedEvent[ObjectStorageClient.Builder]) -> ObjectStorageClient.Builder:
        client_configuration = ClientConfiguration.builder() \
            .connectionTimeoutMillis(self.CONNECTION_TIMEOUT_IN_MILLISECONDS) \
            .readTimeoutMillis(self.READ_TIMEOUT_IN_MILLISECONDS) \
            .build()

        return event.getBean() \
            .configuration(client_configuration)
# end::class[]
