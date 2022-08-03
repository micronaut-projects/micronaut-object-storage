package io.micronaut.objectstorage.googlecloud


import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Requires

@Requires({ System.getenv("GCLOUD_TEST_PROJECT_ID") && System.getenv("GCLOUD_TEST_BUCKET_NAME") })
@MicronautTest
class GoogleCloudObjectStorageSpecOperations extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    GoogleCloudObjectStorage cloudObjectStorage


    @Override
    Map<String, String> getProperties() {
        [
                "gcp.project-id"                                                    : System.getenv("GCLOUD_TEST_PROJECT_ID"),
                (GoogleCloudObjectStorageConfiguration.PREFIX + ".test-bucket.name"): System.getenv("GCLOUD_TEST_BUCKET_NAME"),
        ]
    }

    ObjectStorageOperations getObjectStorage() {
        return cloudObjectStorage
    }

}
