package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import io.micronaut.objectstorage.request.PresignRequest
import io.micronaut.objectstorage.response.PresignResponse
import java.time.Duration
import java.net.URI

//tag::beginclass[]
@Singleton
open class ProfileService(private val objectStorage: ObjectStorageOperations<*, *, *>) {
//end::beginclass[]

    //tag::upload[]
    open fun saveProfilePicture(userId: String, path: Path): String? {
        val request = UploadRequest.fromPath(path, userId) // <1>
        val response = objectStorage.upload(request) // <2>
        return response.key // <3>
    }
    //end::upload[]

    //tag::retrieve[]
    open fun retrieveProfilePicture(userId: String, fileName: String): Path? {
        val key = "$userId/$fileName"
        val stream = objectStorage.retrieve<ObjectStorageEntry<*>>(key) // <1>
            .map { obj: ObjectStorageEntry<*> -> obj.inputStream }

        return if (stream.isPresent) {
            val destination = File.createTempFile(userId, "temp").toPath()
            Files.copy(stream.get(), destination, StandardCopyOption.REPLACE_EXISTING)
            destination
        } else {
            null
        }
    }
    //end::retrieve[]

    //tag::delete[]
    open fun deleteProfilePicture(userId: String, fileName: String) {
        val key = "$userId/$fileName"
        objectStorage.delete(key) // <1>
    }
    //end::delete[]

    //tag::presign[]
    open fun generateDownloadUrl(userId: String, fileName: String): String {
        val key = "$userId/$fileName"
        val request = PresignRequest.builder(key, PresignRequest.Operation.DOWNLOAD)
            .expiresIn(Duration.ofMinutes(15)) // <1>
            .build()
        val response: PresignResponse = objectStorage.presign(request) // <2>
        return response.url().toString() // <3>
    }
    //end::presign[]

    //tag::invalidate[]
    open fun invalidatePresignedUrl(presignResponse: PresignResponse) {
        objectStorage.invalidatePresignedRequest(presignResponse) // <1>
    }
    //end::invalidate[]

//tag::endclass[]
}
//end::endclass[]
