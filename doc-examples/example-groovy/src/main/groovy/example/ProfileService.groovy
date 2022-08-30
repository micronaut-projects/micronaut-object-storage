package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.UploadRequest
import io.micronaut.objectstorage.UploadResponse
import jakarta.inject.Inject
import jakarta.inject.Singleton

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

//tag::beginclass[]
@Singleton
class ProfileService {

    @Inject
    ObjectStorageOperations objectStorage
//end::beginclass[]

    //tag::upload[]
    Optional<String> saveProfilePicture(String userId, Path path) {
        try {
            UploadRequest request = UploadRequest.fromPath(path, userId) // <1>
            UploadResponse response = objectStorage.upload(request) // <2>
            Optional.ofNullable(response.getETag())
        } catch (ObjectStorageException e) {
            return Optional.empty()
        }
    }
    //end::upload[]

    //tag::retrieve[]
    Optional<Path> retrieveProfilePicture(String userId, String fileName) {
        try {
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
        } catch (ObjectStorageException | IOException e) {
            return Optional.empty()
        }
    }
    //end::retrieve[]

    //tag::delete[]
    void deleteProfilePicture(String userId, String fileName) {
        String key = "${userId}/${fileName}"
        objectStorage.delete(key)
    }
    //end::delete[]

//tag::endclass[]
}
//end::endclass[]
