package example;

import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations;
import io.micronaut.objectstorage.multipart.UploadPartRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class MultipartUploadService {

    private final MultipartObjectStorageOperations<?, ?, ?> multipartObjectStorage;

    public MultipartUploadService(MultipartObjectStorageOperations<?, ?, ?> multipartObjectStorage) {
        this.multipartObjectStorage = multipartObjectStorage;
    }

    //tag::multipart-upload[]
    public String uploadVideo(String key, byte[] nonFinalChunk, byte[] finalChunk) {
        var createResponse = multipartObjectStorage.createMultipartUpload(
            new CreateMultipartUploadRequest(key, "video/mp4")
        );
        var upload = createResponse.getUpload();

        try {
            var firstPart = multipartObjectStorage.uploadPart(
                new UploadPartRequest(upload, 1, UploadRequest.fromBytes(nonFinalChunk, upload.getKey()))
            );
            var secondPart = multipartObjectStorage.uploadPart(
                new UploadPartRequest(upload, 2, UploadRequest.fromBytes(finalChunk, upload.getKey()))
            );

            multipartObjectStorage.completeMultipartUpload(
                new CompleteMultipartUploadRequest(upload, List.of(firstPart.getPart(), secondPart.getPart()))
            );
            return upload.getKey();
        } catch (RuntimeException e) {
            multipartObjectStorage.abortMultipartUpload(new AbortMultipartUploadRequest(upload));
            throw e;
        }
    }
    //end::multipart-upload[]
}
