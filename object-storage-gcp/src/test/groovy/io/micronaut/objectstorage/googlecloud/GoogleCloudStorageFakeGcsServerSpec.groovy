package io.micronaut.objectstorage.googlecloud

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@IgnoreIf({ env.GCLOUD_TEST_PROJECT_ID })
@MicronautTest
class GoogleCloudStorageFakeGcsServerSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = System.currentTimeMillis()

    public static final String OBJECT_STORAGE_NAME = "default"

    @Shared
    @AutoCleanup
    static GenericContainer fakeGcs = new GenericContainer(DockerImageName.parse("fsouza/fake-gcs-server"))
            .withExposedPorts(4443)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(
                    "/bin/fake-gcs-server",
                    "-scheme", "http"
            ))
    public static final String TEST_PROJECT_ID = "test-project"

    void setupSpec() {
        fakeGcs.start()
        String fakeGcsExternalUrl = "http://" + fakeGcs.getHost() + ":" + fakeGcs.getFirstMappedPort()
        updateExternalUrlWithContainerUrl(fakeGcsExternalUrl)
    }

    static void updateExternalUrlWithContainerUrl(String fakeGcsExternalUrl) throws Exception {
        String modifyExternalUrlRequestUri = fakeGcsExternalUrl + "/_internal/config"
        def json = """
        {
            "externalUrl": "${fakeGcsExternalUrl}"
        }
        """.trim()
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(modifyExternalUrlRequestUri))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<Void> response = HttpClient.newBuilder().build()
                .send(req, HttpResponse.BodyHandlers.discarding())

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "error updating fake-gcs-server with external url, response status code " + response.statusCode() + " != 200");
        }
    }

    @Inject
    GoogleCloudStorageOperations cloudObjectStorage

    @Inject
    Storage storage

    @Override
    Map<String, String> getProperties() {
        [
                "gcp.project-id"                                                                    : TEST_PROJECT_ID,
                ("${GoogleCloudStorageConfiguration.PREFIX}.${OBJECT_STORAGE_NAME}.name".toString()): BUCKET_NAME,
        ]
    }

    ObjectStorageOperations getObjectStorage() {
        return cloudObjectStorage
    }

    void setup() {
        storage.create(BucketInfo.newBuilder(BUCKET_NAME).build());
    }

    void cleanup() {
        storage.get(BUCKET_NAME).delete()
    }

    @Factory
    static class FakeGcsFactory {

        @Singleton
        @Primary
        Storage storage() {
            String fakeGcsExternalUrl = "http://" + fakeGcs.getHost() + ":" + fakeGcs.getFirstMappedPort()
            StorageOptions.newBuilder()
                    .setHost(fakeGcsExternalUrl)
                    .setProjectId(TEST_PROJECT_ID)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService()
        }

    }
}
