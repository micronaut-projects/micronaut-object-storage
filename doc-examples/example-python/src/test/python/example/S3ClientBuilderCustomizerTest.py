from typing import Annotated

from jakarta.inject import Inject
from java.time import Duration
from java.time.temporal import ChronoUnit
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from software.amazon.awssdk.services.s3 import S3Client


@Property(name="aws.region", value="us-east-1")
@MicronautTest(startApplication=False)
class S3ClientBuilderCustomizerTest:
    client: Annotated[S3Client, Inject]

    @Test
    def test_it_can_customize_s3_client(self):
        configuration = self.client.serviceClientConfiguration().overrideConfiguration()

        assert configuration.apiCallTimeout().isPresent()
        assert configuration.apiCallTimeout().get().equals(Duration.of(60, ChronoUnit.SECONDS))
