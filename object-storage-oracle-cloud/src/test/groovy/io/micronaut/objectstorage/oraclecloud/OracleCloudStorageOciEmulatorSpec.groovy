package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.objectstorage.ObjectStorageClient
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.annotation.NonNull
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Singleton
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

import static io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration.PREFIX

@MicronautTest
@Property(name = 'spec.name', value = SPEC_NAME)
class OracleCloudStorageOciEmulatorSpec extends AbstractOracleCloudStorageSpec {

    public static final String SPEC_NAME = 'OracleCloudStorageOciEmulatorSpec'

    boolean supportsEtag = false

    @Shared
    @AutoCleanup
    GenericContainer ociEmulator = new GenericContainer(DockerImageName.parse('cameritelabs/oci-emulator'))
            .withExposedPorts(12000)

    public static String endpoint

    @Override
    Map<String, String> getProperties() {
        ociEmulator.start()
        endpoint = 'http://127.0.0.1:' + ociEmulator.getMappedPort(12000)
        return super.getProperties() + [
                (PREFIX + '.' + OBJECT_STORAGE_NAME + '.namespace'): 'testtenancy',
                'oci.fingerprint'                                  : '50:a6:c1:a1:da:71:57:dc:87:ae:90:af:9c:38:99:67',
                'oci.private-key-file'                             : "file:${new File('src/test/resources/key.pem').absolutePath}",
                'oci.region'                                       : 'sa-saopaulo-1',
                'oci.tenant-id'                                    : 'ocid1.tenancy.oc1..testtenancy',
                'oci.user-id'                                      : 'ocid1.user.oc1..testuser'
        ]
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class ObjectStorageListener implements BeanCreatedEventListener<ObjectStorageClient> {

        @Override
        ObjectStorageClient onCreated(@NonNull BeanCreatedEvent<ObjectStorageClient> event) {
            ObjectStorageClient client = event.bean
            client.endpoint = endpoint
            return client
        }
    }
}
