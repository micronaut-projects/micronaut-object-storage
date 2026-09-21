from typing import Annotated

from com.azure.storage.blob import BlobServiceClient
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="micronaut.object-storage.azure.default.endpoint", value="https://127.0.0.1:10000/devstoreaccount1")
@MicronautTest(startApplication=False)
class BlobServiceClientBuilderCustomizerTest:
    client: Annotated[BlobServiceClient, Inject]

    @Test
    def test_it_can_customize_the_azure_client(self):
        pipeline = self.client.getHttpPipeline()
        policies = [pipeline.getPolicy(i) for i in range(pipeline.getPolicyCount())]

        # a Python callable converted to a functional interface (the no-op `HttpPipelinePolicy` lambda of the
        # customizer) is the original callable again when it is returned to Python
        assert any(callable(policy) and getattr(policy, "__name__", None) == "<lambda>" for policy in policies)
