package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import jakarta.inject.Singleton

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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

//tag::endclass[]
}
//end::endclass[]
