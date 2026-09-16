from typing import Annotated

from jakarta.inject import Inject, Named
from java.lang import String
from micronaut.context.annotation import Property
from micronaut.objectstorage import ObjectStorageOperations
from micronaut.objectstorage.request import UploadRequest
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.LogoService import LogoService


# The LocalStack container and its S3 properties come from the Java example.LocalStackTestConfigurer
# (@ContextConfigurer, src/test/java) of the "ec2" environment.
@Property(name="micronaut.object-storage.aws.pictures.bucket", value="pictures-bucket")
@MicronautTest(environments=["ec2"])
class LogoServiceTest:
    pictures: Annotated[ObjectStorageOperations, Inject, Named("pictures")]
    logo_service: Annotated[LogoService, Inject]

    @Test
    def test_it_resolves_objects_by_uri(self):
        self.pictures.upload(UploadRequest.fromBytes("micronaut".encode("utf-8"), "avatars/logo.png"))

        logo = self.logo_service.retrieve_logo()

        assert logo is not None
        assert String(logo.readAllBytes(), "UTF-8") == "micronaut"
