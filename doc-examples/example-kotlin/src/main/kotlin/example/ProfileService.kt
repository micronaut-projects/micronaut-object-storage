package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.ListObjectsResponse
import jakarta.inject.Singleton
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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

    //tag::input-stream-upload[]
    open fun saveProfilePicture(userId: String, inputStream: InputStream, contentLength: Long): String? {
        val request = UploadRequest.fromInputStream(
            inputStream,
            "$userId/profile.jpg",
            "image/jpeg",
            contentLength // <1>
        )
        request.metadata = mapOf("source" to "validated-upload") // <2>
        return objectStorage.upload(request).key
    }
    //end::input-stream-upload[]

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

    //tag::paginated-listing[]
    open fun listProfilePicturesByPage(userId: String) {
        val prefix = "$userId/" // raw prefix filtering
        var continuation: String? = null
        do {
            val request = ListObjectsRequest(2, prefix, continuation) // <1>
            val response: ListObjectsResponse = objectStorage.listObjects(request)

            // Process the keys in this page
            response.keys.forEach { key -> println("Found: $key") } // <2>

            // Replay the continuation token returned by the provider for the next page
            continuation = response.continuationToken.orElse(null) // <3>
        } while (continuation != null)
    }
    //end::paginated-listing[]

//tag::endclass[]
}
//end::endclass[]
