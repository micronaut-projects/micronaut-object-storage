package example

import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton

@Singleton
open class MultipartUploadService(
    private val multipartObjectStorage: MultipartObjectStorageOperations<*, *, *>
) {

    //tag::multipart-upload[]
    open fun uploadVideo(key: String, nonFinalChunk: ByteArray, finalChunk: ByteArray): String {
        val createResponse = multipartObjectStorage.createMultipartUpload(
            CreateMultipartUploadRequest(key, "video/mp4")
        )
        val upload = createResponse.upload

        try {
            val firstPart = multipartObjectStorage.uploadPart(
                UploadPartRequest(upload, 1, UploadRequest.fromBytes(nonFinalChunk, upload.key))
            )
            val secondPart = multipartObjectStorage.uploadPart(
                UploadPartRequest(upload, 2, UploadRequest.fromBytes(finalChunk, upload.key))
            )

            multipartObjectStorage.completeMultipartUpload(
                CompleteMultipartUploadRequest(upload, listOf(firstPart.part, secondPart.part))
            )
            return upload.key
        } catch (e: RuntimeException) {
            multipartObjectStorage.abortMultipartUpload(AbortMultipartUploadRequest(upload))
            throw e
        }
    }
    //end::multipart-upload[]
}
