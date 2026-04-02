package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import io.micronaut.objectstorage.response.PresignedUpload

import java.time.Duration
import java.time.Instant

//tag::beginclass[]
@Controller('/uploads')
class PresignedUploadController {

    final ObjectStorageOperations<?, ?, ?> objectStorage

    PresignedUploadController(ObjectStorageOperations<?, ?, ?> objectStorage) {
        this.objectStorage = objectStorage
    }
//end::beginclass[]

    //tag::presigned-upload[]
    @Post(uri = '/signed', consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<PresignedUploadResponse> create(@Body CreateUploadCommand command) {
        CreatePresignedUploadRequest request =
            new CreatePresignedUploadRequest("profiles/${command.userId}.png", Duration.ofMinutes(5)) // <1>
        request.contentType = MediaType.IMAGE_PNG
        request.metadata = [owner: command.userId]

        PresignedUpload signedUpload = objectStorage.createPresignedUpload(request)
            .orElseThrow(() -> new IllegalStateException('This provider does not support pre-signed uploads'))

        HttpResponse.ok(new PresignedUploadResponse( // <2>
            signedUpload.uri.toString(),
            signedUpload.method,
            signedUpload.headers,
            signedUpload.expiration
        ))
    }
    //end::presigned-upload[]

    static class CreateUploadCommand {
        String userId
    }

    static class PresignedUploadResponse {
        final String url
        final String method
        final Map<String, List<String>> headers
        final Instant expiresAt

        PresignedUploadResponse(String url,
                                String method,
                                Map<String, List<String>> headers,
                                Instant expiresAt) {
            this.url = url
            this.method = method
            this.headers = headers
            this.expiresAt = expiresAt
        }
    }
}
//tag::endclass[]
