package io.micronaut.objectstorage.googlecloud

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

@Requires({ env.GCLOUD_TEST_PROJECT_ID })
@MicronautTest
class GoogleCloudStorageCloudSpec extends AbstractGoogleCloudStorageSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + ['gcp.project-id': System.getenv('GCLOUD_TEST_PROJECT_ID')]
    }
}
