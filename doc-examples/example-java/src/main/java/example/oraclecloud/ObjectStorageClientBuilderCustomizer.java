package example.oraclecloud;

import com.oracle.bmc.http.ClientConfigurator;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.apache.http.client.config.RequestConfig;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

//tag::class[]
//See https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ApacheConnectorPropertiesExample.java
@Singleton
public class ObjectStorageClientBuilderCustomizer implements BeanCreatedEventListener<ObjectStorageClient.Builder> {

    @Override
    public ObjectStorageClient.Builder onCreated(@NonNull BeanCreatedEvent<ObjectStorageClient.Builder> event) {
        ClientConfigurator additionalClientConfigurator =
            new ClientConfigurator() {
                @Override
                public void customizeBuilder(ClientBuilder clientBuilder) {
                    RequestConfig config =
                        RequestConfig.custom()
                            .setConnectTimeout(60_000)
                            .build();
                    clientBuilder.property(ApacheClientProperties.REQUEST_CONFIG, config);
                }

                @Override
                public void customizeClient(Client client) {
                    // no op
                }
            };

        return event.getBean()
            .additionalClientConfigurator(additionalClientConfigurator);
    }
}
//end::class[]
