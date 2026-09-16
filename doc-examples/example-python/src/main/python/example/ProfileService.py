import logging

from jakarta.inject import Singleton
from java.io import File, IOException, InputStream
from java.nio.file import Files, Path, StandardCopyOption
from micronaut.objectstorage import ObjectStorageOperations
from micronaut.objectstorage.request import ListObjectsRequest, UploadRequest

LOG = logging.getLogger(__name__)


# tag::beginclass[]
@Singleton
class ProfileService:

    def __init__(self, object_storage: ObjectStorageOperations):
        self.object_storage = object_storage
# end::beginclass[]

    # tag::upload[]
    def save_profile_picture(self, user_id: str, path: Path) -> str:
        request = UploadRequest.fromPath(path, user_id)  # <1>
        response = self.object_storage.upload(request)  # <2>
        return response.getKey()  # <3>
    # end::upload[]

    # tag::input-stream-upload[]
    def save_profile_picture_stream(self, user_id: str, input_stream: InputStream, content_length: int) -> str:
        request = UploadRequest.fromInputStream(
            input_stream,
            user_id + "/profile.jpg",
            "image/jpeg",
            content_length  # <1>
        )
        request.setMetadata({"source": "validated-upload"})  # <2>
        return self.object_storage.upload(request).getKey()
    # end::input-stream-upload[]

    # tag::retrieve[]
    def retrieve_profile_picture(self, user_id: str, file_name: str) -> Path | None:
        destination = None
        try:
            key = user_id + "/" + file_name
            stream = self.object_storage.retrieve(key).map(lambda entry: entry.getInputStream())  # <1>
            if stream.isPresent():
                destination = File.createTempFile(user_id, "temp").toPath()
                Files.copy(stream.get(), destination, StandardCopyOption.REPLACE_EXISTING)
                return destination
            else:
                return None
        except IOException as e:
            LOG.error("Error while trying to save profile picture to the local file [%s]: %s", destination, e.getMessage())
            return None
    # end::retrieve[]

    # tag::delete[]
    def delete_profile_picture(self, user_id: str, file_name: str) -> None:
        key = user_id + "/" + file_name
        self.object_storage.delete(key)  # <1>
    # end::delete[]

    # tag::paginated-listing[]
    def list_profile_pictures_by_page(self, user_id: str) -> None:
        prefix = user_id + "/"  # raw prefix filtering
        continuation = None
        while True:
            request = ListObjectsRequest(2, prefix, continuation)  # <1>
            response = self.object_storage.listObjects(request)

            # Process the keys in this page
            for key in response.getKeys():
                print("Found: " + key)  # <2>

            # Replay the continuation token returned by the provider for the next page
            continuation = response.getContinuationToken().orElse(None)  # <3>
            if continuation is None:
                break
    # end::paginated-listing[]

# tag::endclass[]
# end::endclass[]
