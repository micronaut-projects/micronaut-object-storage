package example.oraclecloud

import com.oracle.bmc.ClientConfiguration
import com.oracle.bmc.objectstorage.ObjectStorageClient
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

//tag::class[]
//See https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ClientConfigurationTimeoutExample.java
@Singleton
class ObjectStorageClientBuilderCustomizer : BeanCreatedEventListener<ObjectStorageClient.Builder> {

    companion object {
        const val CONNECTION_TIMEOUT_IN_MILLISECONDS = 25000
        const val READ_TIMEOUT_IN_MILLISECONDS = 35000
    }

    override fun onCreated(event: BeanCreatedEvent<ObjectStorageClient.Builder>): ObjectStorageClient.Builder {
        val clientConfiguration = ClientConfiguration.builder()
            .connectionTimeoutMillis(CONNECTION_TIMEOUT_IN_MILLISECONDS)
            .readTimeoutMillis(READ_TIMEOUT_IN_MILLISECONDS)
            .build()

        return event.bean
            .configuration(clientConfiguration)
    }
}
//end::class[]
