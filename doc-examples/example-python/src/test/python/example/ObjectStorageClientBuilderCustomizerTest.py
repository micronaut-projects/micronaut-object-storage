from typing import Annotated

from com.oracle.bmc.auth import AbstractAuthenticationDetailsProvider
from com.oracle.bmc.objectstorage import ObjectStorageClient
from jakarta.inject import Inject, Singleton
from java.lang import Class
from micronaut.context.annotation import Factory, Property, Requires
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.oraclecloud.ObjectStorageClientBuilderCustomizer import ObjectStorageClientBuilderCustomizer


@Property(name="spec.name", value="ObjectStorageClientBuilderCustomizerTest")
@MicronautTest(environments=["oraclecloud"], startApplication=False)
class ObjectStorageClientBuilderCustomizerTest:
    builder: Annotated[ObjectStorageClient.Builder, Inject]

    @Test
    def test_it_can_customise_the_client_timeouts(self):
        # ClientBuilderBase.configuration is a protected field (the Groovy test reads it through Groovy's field access)
        field = Class.forName("com.oracle.bmc.common.ClientBuilderBase").getDeclaredField("configuration")
        field.setAccessible(True)
        configuration = field.get(self.builder)

        assert configuration.getConnectionTimeoutMillis() == ObjectStorageClientBuilderCustomizer.CONNECTION_TIMEOUT_IN_MILLISECONDS
        assert configuration.getReadTimeoutMillis() == ObjectStorageClientBuilderCustomizer.READ_TIMEOUT_IN_MILLISECONDS


class TestAuthenticationDetailsProvider(AbstractAuthenticationDetailsProvider):
    pass


@Factory
@Requires(property="spec.name", value="ObjectStorageClientBuilderCustomizerTest")
class AuthenticationDetailsProviderFactory:

    @Singleton
    def provider(self) -> AbstractAuthenticationDetailsProvider:
        return TestAuthenticationDetailsProvider()
