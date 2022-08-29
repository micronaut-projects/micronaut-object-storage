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

import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.MediaType.APPLICATION_JSON

@IgnoreIf({ env.GCLOUD_TEST_PROJECT_ID })
@MicronautTest
@Property(name = 'spec.name', value = SPEC_NAME)
class GoogleCloudStorageFakeGcsServerSpec extends AbstractGoogleCloudStorageSpec {

    public static final String TEST_PROJECT_ID = 'test-project'
    public static final String SPEC_NAME = 'GoogleCloudStorageFakeGcsServerSpec'

    @Shared
    @AutoCleanup
    static GenericContainer fakeGcs = new GenericContainer(DockerImageName.parse('fsouza/fake-gcs-server'))
            .withExposedPorts(4443)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(
                    '/bin/fake-gcs-server',
                    '-scheme', 'http'
            ))

    void setupSpec() {
        fakeGcs.start()
        String fakeGcsExternalUrl = "http://${fakeGcs.host}:${fakeGcs.firstMappedPort}"
        updateExternalUrlWithContainerUrl(fakeGcsExternalUrl)
    }

    static void updateExternalUrlWithContainerUrl(String fakeGcsExternalUrl) throws Exception {
        String modifyExternalUrlRequestUri = fakeGcsExternalUrl + '/_internal/config'
        String json = """
        {
            "externalUrl": "$fakeGcsExternalUrl"
        }
        """.trim()
        def connection = new URL(modifyExternalUrlRequestUri).openConnection() as HttpURLConnection
        connection.with {
            requestMethod = 'PUT'
            doOutput = true
            setRequestProperty(CONTENT_TYPE, APPLICATION_JSON)
            outputStream.withPrintWriter {it.write(json) }
            def result = inputStream.text

            if (responseCode != 200) {
                throw new RuntimeException(
                        "error updating fake-gcs-server with external url, response status code $responseCode != 200");
            }
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class FakeGcsFactory {

        @Singleton
        @Primary
        Storage storage() {
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
