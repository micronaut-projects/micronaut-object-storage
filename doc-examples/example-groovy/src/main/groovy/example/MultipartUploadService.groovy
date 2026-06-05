package example

import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton

@Singleton
class MultipartUploadService {

    private final MultipartObjectStorageOperations<?, ?, ?> multipartObjectStorage

    MultipartUploadService(MultipartObjectStorageOperations<?, ?, ?> multipartObjectStorage) {
        this.multipartObjectStorage = multipartObjectStorage
    }

    //tag::multipart-upload[]
    String uploadVideo(String key, byte[] nonFinalChunk, byte[] finalChunk) {
        def createResponse = multipartObjectStorage.createMultipartUpload(
            new CreateMultipartUploadRequest(key, 'video/mp4')
        )
        def upload = createResponse.upload

        try {
            def firstPart = multipartObjectStorage.uploadPart(
                new UploadPartRequest(upload, 1, UploadRequest.fromBytes(nonFinalChunk, upload.key))
            )
            def secondPart = multipartObjectStorage.uploadPart(
                new UploadPartRequest(upload, 2, UploadRequest.fromBytes(finalChunk, upload.key))
            )

            multipartObjectStorage.completeMultipartUpload(
                new CompleteMultipartUploadRequest(upload, [firstPart.part, secondPart.part])
            )
            upload.key
        } catch (RuntimeException e) {
            multipartObjectStorage.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
            throw e
        }
    }
    //end::multipart-upload[]
}
