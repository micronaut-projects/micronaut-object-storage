package io.micronaut.objectstorage.googlecloud

import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.ObjectStorage
import io.micronaut.objectstorage.ObjectStorageSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Requires

@Requires({ System.getenv("GCLOUD_TEST_PROJECT_ID") && System.getenv("GCLOUD_TEST_BUCKET_NAME") })
@MicronautTest
class GoogleCloudObjectStorageSpec extends ObjectStorageSpecification implements TestPropertyProvider {

    @Inject
    GoogleCloudObjectStorage cloudObjectStorage


    @Override
    Map<String, String> getProperties() {
        [
                "gcp.project-id"                                        : System.getenv("GCLOUD_TEST_PROJECT_ID"),
                "micronaut.object-storage.google-cloud.test-bucket.name": System.getenv("GCLOUD_TEST_BUCKET_NAME"),
        ]
    }

    ObjectStorage getObjectStorage() {
        return cloudObjectStorage
    }

}
