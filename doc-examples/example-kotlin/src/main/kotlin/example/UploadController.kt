package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.request.UploadRequest
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest

//tag::beginclass[]
@Controller
open class UploadController(private val objectStorage: AwsS3Operations) {
//end::beginclass[]

    @Post(consumes = [MediaType.MULTIPART_FORM_DATA], produces = [MediaType.TEXT_PLAIN])
    open fun upload(fileUpload: CompletedFileUpload): HttpResponse<String>? {
        val objectStorageUpload = UploadRequest.fromCompletedFileUpload(fileUpload)
//tag::consumer[]
        val response = objectStorage.upload(objectStorageUpload) { builder: PutObjectRequest.Builder ->
            builder.acl(ObjectCannedACL.PUBLIC_READ)
        }
//end::consumer[]

        return HttpResponse
            .created(response.key)
            .header("ETag", response.nativeResponse.eTag())
    }

//tag::endclass[]
}
//end::endclass[]
