package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import jakarta.inject.Singleton

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import io.micronaut.objectstorage.request.PresignRequest
import io.micronaut.objectstorage.response.PresignResponse
import java.time.Duration
import java.net.URI

//tag::beginclass[]
@Singleton
class ProfileService {

    final ObjectStorageOperations<?, ?, ?> objectStorage

    ProfileService(ObjectStorageOperations<?, ?, ?> objectStorage) {
        this.objectStorage = objectStorage
    }
//end::beginclass[]

    //tag::upload[]
    String saveProfilePicture(String userId, Path path) {
        UploadRequest request = UploadRequest.fromPath(path, userId) // <1>
        UploadResponse response = objectStorage.upload(request) // <2>
        response.key // <3>
    }
    //end::upload[]

    //tag::retrieve[]
    Optional<Path> retrieveProfilePicture(String userId, String fileName) {
        String key = "${userId}/${fileName}"
        Optional<InputStream> stream = objectStorage.retrieve(key) // <1>
                .map(ObjectStorageEntry::getInputStream)
        if (stream.isPresent()) {
            Path destination = File.createTempFile(userId, "temp").toPath()
            Files.copy(stream.get(), destination, StandardCopyOption.REPLACE_EXISTING)
            return Optional.of(destination)
        } else {
            return Optional.empty()
        }
    }
    //end::retrieve[]

    //tag::delete[]
    void deleteProfilePicture(String userId, String fileName) {
        String key = "${userId}/${fileName}"
        objectStorage.delete(key) // <1>
    }
    //end::delete[]

    //tag::presign[]
    String generateDownloadUrl(String userId, String fileName) {
        String key = "${userId}/${fileName}"
        PresignRequest request = PresignRequest.builder(key, PresignRequest.Operation.DOWNLOAD)
                .expiresIn(Duration.ofMinutes(15)) // <1>
                .build()
        PresignResponse response = objectStorage.presign(request) // <2>
        response.url().toString() // <3>
    }
    //end::presign[]

    //tag::invalidate[]
    void invalidatePresignedUrl(PresignResponse presignResponse) {
        objectStorage.invalidatePresignedRequest(presignResponse) // <1>
    }
    //end::invalidate[]

//tag::endclass[]
}
//end::endclass[]
