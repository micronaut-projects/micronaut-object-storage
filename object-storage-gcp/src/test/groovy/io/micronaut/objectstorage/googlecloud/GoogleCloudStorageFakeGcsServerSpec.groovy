package io.micronaut.objectstorage.googlecloud

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Singleton
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

@IgnoreIf({ env.GCLOUD_TEST_PROJECT_ID })
@MicronautTest
@Property(name = 'spec.name', value = SPEC_NAME)
class GoogleCloudStorageFakeGcsServerSpec extends AbstractGoogleCloudStorageSpec {

    public static final String TEST_PROJECT_ID = 'test-project'
    public static final String SPEC_NAME = 'GoogleCloudStorageFakeGcsServerSpec'
    private static final DockerImageName FAKE_GCS_IMAGE = DockerImageName.parse('fsouza/fake-gcs-server:1.53.0')

    @Shared
    @AutoCleanup
    static final GenericContainer<?> fakeGcs = new GenericContainer<>(FAKE_GCS_IMAGE)
            .withExposedPorts(4443)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(
                    "/bin/fake-gcs-server",
                    "-scheme", "http"
            ))

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class FakeGcsFactory {

        @Singleton
        @Primary
        Storage storage() {
            if (!fakeGcs.running) {
                fakeGcs.start()
            }
            String fakeGcsExternalUrl = "http://${fakeGcs.host}:${fakeGcs.firstMappedPort}"
            StorageOptions.newBuilder()
                    .setHost(fakeGcsExternalUrl)
                    .setProjectId(TEST_PROJECT_ID)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService()
        }
    }
}
