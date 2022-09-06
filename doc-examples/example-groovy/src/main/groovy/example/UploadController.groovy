package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectResponse

//tag::beginclass[]
@Controller
class UploadController {

    final AwsS3Operations objectStorage

    UploadController(AwsS3Operations objectStorage) {
        this.objectStorage = objectStorage
    }
//end::beginclass[]

    @Post(consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    HttpResponse<String> upload(CompletedFileUpload fileUpload) {
        UploadRequest objectStorageUpload = UploadRequest.fromCompletedFileUpload(fileUpload)
//tag::consumer[]
        UploadResponse<PutObjectResponse> response = objectStorage.upload(objectStorageUpload, { builder ->
            builder.acl(ObjectCannedACL.PUBLIC_READ)
        })
//end::consumer[]

        return HttpResponse
                .created(response.key)
                .header("ETag", response.getNativeResponse().eTag())
    }

//tag::endclass[]
}
//end::endclass[]
