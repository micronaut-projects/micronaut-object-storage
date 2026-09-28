from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Controller, Post
from micronaut.http.multipart import CompletedFileUpload, StreamingFileUpload
from micronaut.objectstorage.aws import AwsS3Operations
from micronaut.objectstorage.request import UploadRequest
from software.amazon.awssdk.services.s3.model import ObjectCannedACL


# tag::beginclass[]
@Controller
class UploadController:

    def __init__(self, object_storage: AwsS3Operations):
        self.object_storage = object_storage
# end::beginclass[]

    @Post(consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)
    def upload(self, file_upload: CompletedFileUpload) -> HttpResponse[str]:
        object_storage_upload = UploadRequest.fromCompletedFileUpload(file_upload)
# tag::consumer[]
        response = self.object_storage.upload(object_storage_upload, lambda builder: builder.acl(ObjectCannedACL.PUBLIC_READ))
# end::consumer[]

        return HttpResponse \
            .created(response.getKey()) \
            .header("ETag", response.getNativeResponse().eTag())

# tag::streaming[]
    @Post(uri="/stream", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)
    def streaming_upload(self, file_upload: StreamingFileUpload) -> HttpResponse[str]:
        object_storage_upload = UploadRequest.fromStreamingFileUpload(file_upload, "uploads/" + file_upload.getFilename())
        response = self.object_storage.upload(object_storage_upload)
        return HttpResponse \
            .created(response.getKey()) \
            .header("ETag", response.getNativeResponse().eTag())
# end::streaming[]

# tag::endclass[]
# end::endclass[]
