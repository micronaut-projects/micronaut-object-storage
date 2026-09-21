from typing import Annotated

from com.google.auth.oauth2 import AccessToken, GoogleCredentials
from com.google.cloud.storage import StorageOptions
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Factory, Primary, Property, Requires
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="gcp.project-id", value="test-project-basdasdasd")
@Property(name="gcp.credentials.enabled", value="false")
@Property(name="spec.name", value="StorageOptionsBuilderCustomizerTest")
@MicronautTest(startApplication=False)
class StorageOptionsBuilderCustomizerTest:
    client: Annotated[StorageOptions, Inject]

    @Test
    def test_it_can_customize_the_gcp_client(self):
        assert self.client.getTransportOptions().getConnectTimeout() == 60_000


@Factory
@Requires(property="spec.name", value="StorageOptionsBuilderCustomizerTest")
class TestCredentialsFactory:

    @Singleton
    @Primary
    def google_credentials(self) -> GoogleCredentials:
        return GoogleCredentials.create(AccessToken("test-token", None))
