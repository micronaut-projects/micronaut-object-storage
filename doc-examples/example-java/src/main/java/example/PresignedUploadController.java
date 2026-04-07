package example;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest;
import io.micronaut.objectstorage.response.PresignedUpload;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

//tag::beginclass[]
@Controller("/uploads")
public class PresignedUploadController {

    private final ObjectStorageOperations<?, ?, ?> objectStorage;

    public PresignedUploadController(ObjectStorageOperations<?, ?, ?> objectStorage) {
        this.objectStorage = objectStorage;
    }
//end::beginclass[]

    //tag::presigned-upload[]
    @Post(uri = "/signed", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<PresignedUploadResponse> create(@Body CreateUploadCommand command) {
        CreatePresignedUploadRequest request =
            new CreatePresignedUploadRequest("profiles/" + command.userId() + ".png", Duration.ofMinutes(5)); // <1>
        request.setContentType(MediaType.IMAGE_PNG);
        request.setMetadata(Map.of("owner", command.userId()));

        PresignedUpload signedUpload = objectStorage.createPresignedUpload(request)
            .orElseThrow(() -> new IllegalStateException("This provider does not support pre-signed uploads"));

        return HttpResponse.ok(new PresignedUploadResponse( // <2>
            signedUpload.getUri().toString(),
            signedUpload.getMethod(),
            signedUpload.getHeaders(),
            signedUpload.getExpiration()
        ));
    }
    //end::presigned-upload[]

    public record CreateUploadCommand(String userId) {
    }

    public record PresignedUploadResponse(String url,
                                          String method,
                                          Map<String, List<String>> headers,
                                          Instant expiresAt) {
    }
}
//tag::endclass[]
