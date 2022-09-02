package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

//tag::beginclass[]
@Singleton
open class ProfileService(private val objectStorage: ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse>) {
//end::beginclass[]

    //tag::upload[]
    open fun saveProfilePicture(userId: String?, path: Path): String? {
        val request = UploadRequest.fromPath(path, userId) // <1>
        val response = objectStorage.upload(request) // <2>
        return response.eTag()
    }
    //end::upload[]

    //tag::retrieve[]
    open fun retrieveProfilePicture(userId: String, fileName: String): Path? {
        val key = "$userId/$fileName"
        val stream = objectStorage.retrieve(key) // <1>
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
        objectStorage.delete(key)
    }
    //end::delete[]

//tag::endclass[]
}
//end::endclass[]
