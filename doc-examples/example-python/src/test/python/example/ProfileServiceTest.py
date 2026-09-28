from typing import Annotated

from jakarta.inject import Inject
from java.nio.file import Files
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ProfileService import ProfileService

USER_ID = "user123"


# The LocalStack container and its S3 properties come from the Java example.LocalStackTestConfigurer
# (@ContextConfigurer, src/test/java) of the "ec2" environment.
@MicronautTest(environments=["ec2"])
class ProfileServiceTest:
    service: Annotated[ProfileService, Inject]

    @Test
    def test_it_works(self):
        path = Files.createTempFile("test-file", ".txt")
        Files.writeString(path, "micronaut")
        file_name = path.toFile().getName()

        assert self.service.retrieve_profile_picture(USER_ID, file_name) is None

        save_result = self.service.save_profile_picture(USER_ID, path)
        assert save_result

        retrieve_result = self.service.retrieve_profile_picture(USER_ID, file_name)
        assert retrieve_result is not None
        assert Files.readString(retrieve_result) == "micronaut"

        self.service.delete_profile_picture(USER_ID, file_name)
        assert self.service.retrieve_profile_picture(USER_ID, file_name) is None

    @Test
    def test_input_stream_upload(self):
        path = Files.createTempFile("test-file", ".txt")
        Files.writeString(path, "micronaut")

        with_stream = Files.newInputStream(path)
        key = self.service.save_profile_picture_stream(USER_ID, with_stream, Files.size(path))
        assert key == USER_ID + "/profile.jpg"

        retrieve_result = self.service.retrieve_profile_picture(USER_ID, "profile.jpg")
        assert retrieve_result is not None
        assert Files.readString(retrieve_result) == "micronaut"

        self.service.delete_profile_picture(USER_ID, "profile.jpg")

    @Test
    def test_paginated_listing(self):
        for i in range(3):
            path = Files.createTempFile("test-file", ".txt")
            Files.writeString(path, "micronaut")
            self.service.save_profile_picture("pages", path)
        # walks every page of the "pages/" prefix without throwing
        self.service.list_profile_pictures_by_page("pages")
