package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import java.time.Duration
import java.time.Instant

//tag::beginclass[]
@Controller("/uploads")
open class PresignedUploadController(private val objectStorage: ObjectStorageOperations<*, *, *>) {
//end::beginclass[]

    //tag::presigned-upload[]
    @Post(uri = "/signed", consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    open fun create(@Body command: CreateUploadCommand): HttpResponse<PresignedUploadResponse> {
        val request = CreatePresignedUploadRequest("profiles/${command.userId}.png", Duration.ofMinutes(5)) // <1>
        request.setContentType(MediaType.IMAGE_PNG)
        request.setMetadata(mapOf("owner" to command.userId))

        val signedUpload = objectStorage.createPresignedUpload(request)
            .orElseThrow { IllegalStateException("This provider does not support pre-signed uploads") }

        return HttpResponse.ok(
            PresignedUploadResponse( // <2>
                signedUpload.uri.toString(),
                signedUpload.method,
                signedUpload.headers,
                signedUpload.expiration
            )
        )
    }
    //end::presigned-upload[]

    data class CreateUploadCommand(val userId: String)

    data class PresignedUploadResponse(
        val url: String,
        val method: String,
        val headers: Map<String, List<String>>,
        val expiresAt: Instant
    )
}
//tag::endclass[]
