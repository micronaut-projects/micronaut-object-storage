from dataclasses import dataclass
from typing import Annotated

from java.lang import IllegalStateException
from java.time import Duration, Instant
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Controller, Post
from micronaut.objectstorage import ObjectStorageOperations
from micronaut.objectstorage.request import CreatePresignedUploadRequest
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class CreateUploadCommand:
    userId: str


@Introspected
@dataclass
class PresignedUploadResponse:
    url: str
    method: str
    headers: dict[str, list[str]]
    expiresAt: Instant


# tag::beginclass[]
@Controller("/uploads")
class PresignedUploadController:

    def __init__(self, object_storage: ObjectStorageOperations):
        self.object_storage = object_storage
# end::beginclass[]

    # tag::presigned-upload[]
    @Post(uri="/signed", consumes=MediaType.APPLICATION_JSON, produces=MediaType.APPLICATION_JSON)
    def create(self, command: Annotated[CreateUploadCommand, Body]) -> HttpResponse[PresignedUploadResponse]:
        request = CreatePresignedUploadRequest("profiles/" + command.userId + ".png", Duration.ofMinutes(5))  # <1>
        request.setContentType(MediaType.IMAGE_PNG)
        request.setMetadata({"owner": command.userId})

        signed_upload = self.object_storage.createPresignedUpload(request) \
            .orElseThrow(lambda: IllegalStateException("This provider does not support pre-signed uploads"))

        return HttpResponse.ok(PresignedUploadResponse(  # <2>
            signed_upload.getUri().toString(),
            signed_upload.getMethod(),
            signed_upload.getHeaders(),
            signed_upload.getExpiration()
        ))
    # end::presigned-upload[]
# tag::endclass[]
# end::endclass[]
