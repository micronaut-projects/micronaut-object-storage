package io.micronaut.objectstorage.googlecloud

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Singleton
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.MediaType.APPLICATION_JSON

@IgnoreIf({ env.GCLOUD_TEST_PROJECT_ID })
@MicronautTest
@Property(name = 'spec.name', value = SPEC_NAME)
class GoogleCloudStorageFakeGcsServerSpec extends AbstractGoogleCloudStorageSpec {

    public static final String TEST_PROJECT_ID = 'test-project'
    public static final String SPEC_NAME = 'GoogleCloudStorageFakeGcsServerSpec'

    @Shared
    static int port = SocketUtils.findAvailableTcpPort()

    @Shared
    @AutoCleanup
    static GenericContainer fakeGcs = new FixedHostPortGenericContainer('fsouza/fake-gcs-server:1.40.1')
            .withFixedExposedPort(port, 4443)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(
                    '/bin/fake-gcs-server',
                    '-scheme', 'http'
            ))

    void setupSpec() {
        fakeGcs.start()
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class FakeGcsFactory {

        @Singleton
        @Primary
        Storage storage() {
            String fakeGcsExternalUrl = "http://${fakeGcs.host}:${port}"
            StorageOptions.newBuilder()
                    .setHost(fakeGcsExternalUrl)
                    .setProjectId(TEST_PROJECT_ID)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService()
        }
    }
}
