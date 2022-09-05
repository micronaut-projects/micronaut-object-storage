package example;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.objectstorage.aws.AwsS3Operations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Controller
public class UploadController {

    private final AwsS3Operations objectStorage;

    public UploadController(AwsS3Operations objectStorage) {
        this.objectStorage = objectStorage;
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> upload(CompletedFileUpload fileUpload) {
        UploadRequest objectStorageUpload = UploadRequest.fromCompletedFileUpload(fileUpload);
        UploadResponse<PutObjectResponse> response = objectStorage.upload(objectStorageUpload, builder -> {
            builder.acl(ObjectCannedACL.PUBLIC_READ);
        });

        return HttpResponse.created(response.getNativeResponse().eTag());
    }
}
