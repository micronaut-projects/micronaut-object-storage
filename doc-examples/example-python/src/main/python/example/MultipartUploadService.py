from jakarta.inject import Singleton
from java.lang import RuntimeException
from micronaut.objectstorage.multipart import (
    AbortMultipartUploadRequest,
    CompleteMultipartUploadRequest,
    CreateMultipartUploadRequest,
    MultipartObjectStorageOperations,
    UploadPartRequest,
)
from micronaut.objectstorage.request import UploadRequest


@Singleton
class MultipartUploadService:

    def __init__(self, multipart_object_storage: MultipartObjectStorageOperations):
        self.multipart_object_storage = multipart_object_storage

    # tag::multipart-upload[]
    def upload_video(self, key: str, non_final_chunk: bytes, final_chunk: bytes) -> str:
        create_response = self.multipart_object_storage.createMultipartUpload(
            CreateMultipartUploadRequest(key, "video/mp4")
        )
        upload = create_response.getUpload()

        try:
            first_part = self.multipart_object_storage.uploadPart(
                UploadPartRequest(upload, 1, UploadRequest.fromBytes(non_final_chunk, upload.getKey()))
            )
            second_part = self.multipart_object_storage.uploadPart(
                UploadPartRequest(upload, 2, UploadRequest.fromBytes(final_chunk, upload.getKey()))
            )

            self.multipart_object_storage.completeMultipartUpload(
                CompleteMultipartUploadRequest(upload, [first_part.getPart(), second_part.getPart()])
            )
            return upload.getKey()
        except RuntimeException as e:
            self.multipart_object_storage.abortMultipartUpload(AbortMultipartUploadRequest(upload))
            raise e
    # end::multipart-upload[]
