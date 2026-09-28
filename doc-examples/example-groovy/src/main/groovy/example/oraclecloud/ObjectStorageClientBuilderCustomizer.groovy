package example.oraclecloud

import com.oracle.bmc.ClientConfiguration
import com.oracle.bmc.objectstorage.ObjectStorageClient
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

//tag::class[]
//See https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ClientConfigurationTimeoutExample.java
@Singleton
class ObjectStorageClientBuilderCustomizer implements BeanCreatedEventListener<ObjectStorageClient.Builder> {

    public static final int CONNECTION_TIMEOUT_IN_MILLISECONDS = 25000
    public static final int READ_TIMEOUT_IN_MILLISECONDS = 35000

    @Override
    ObjectStorageClient.Builder onCreated(BeanCreatedEvent<ObjectStorageClient.Builder> event) {
        ClientConfiguration clientConfiguration =
            ClientConfiguration.builder()
                .connectionTimeoutMillis(CONNECTION_TIMEOUT_IN_MILLISECONDS)
                .readTimeoutMillis(READ_TIMEOUT_IN_MILLISECONDS)
                .build()

        event.bean
            .configuration(clientConfiguration)
    }
}
//end::class[]
