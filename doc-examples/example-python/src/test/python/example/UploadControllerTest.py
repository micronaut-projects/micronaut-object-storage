from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from java.nio.file import Files
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.multipart import MultipartBody
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


# The LocalStack container and its S3 properties come from the Java example.LocalStackTestConfigurer
# (@ContextConfigurer, src/test/java) of the "ec2" environment.
@MicronautTest(environments=["ec2"])
class UploadControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_it_works(self):
        response = self.upload("/")

        assert response.status() == HttpStatus.CREATED
        assert response.getBody().isPresent()
        assert response.header("ETag")

    @Test
    def test_streaming_upload_works(self):
        response = self.upload("/stream")

        assert response.status() == HttpStatus.CREATED
        assert response.getBody().get().startswith("uploads/")
        assert response.header("ETag")

    def upload(self, uri: str):
        path = Files.createTempFile("test-file", ".txt")
        Files.writeString(path, "micronaut")
        file = path.toFile()
        request_body = MultipartBody \
            .builder() \
            .addPart("file_upload", file.getName(), MediaType.TEXT_PLAIN_TYPE, file) \
            .build()
        request = HttpRequest \
            .POST(uri, request_body) \
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        return self.client.toBlocking().exchange(request, String)
