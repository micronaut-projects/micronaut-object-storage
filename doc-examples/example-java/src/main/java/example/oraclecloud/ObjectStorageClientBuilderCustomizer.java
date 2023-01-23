package example.oraclecloud;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

//tag::class[]
//See https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ClientConfigurationTimeoutExample.java
@Singleton
public class ObjectStorageClientBuilderCustomizer implements BeanCreatedEventListener<ObjectStorageClient.Builder> {

    public static final int CONNECTION_TIMEOUT_IN_MILLISECONDS = 25000;
    public static final int READ_TIMEOUT_IN_MILLISECONDS = 35000;

    @Override
    public ObjectStorageClient.Builder onCreated(@NonNull BeanCreatedEvent<ObjectStorageClient.Builder> event) {
        ClientConfiguration clientConfiguration =
            ClientConfiguration.builder()
                .connectionTimeoutMillis(CONNECTION_TIMEOUT_IN_MILLISECONDS)
                .readTimeoutMillis(READ_TIMEOUT_IN_MILLISECONDS)
                .build();


        return event.getBean()
            .configuration(clientConfiguration);
    }
}
//end::class[]
