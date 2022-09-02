package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

//tag::beginclass[]
@Singleton
class ProfileService {

    final ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, ?> objectStorage
    ProfileService(ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, ?> objectStorage) {
        this.objectStorage = objectStorage
    }
//end::beginclass[]

    //tag::upload[]
    Optional<String> saveProfilePicture(String userId, Path path) {
        try {
            UploadRequest request = UploadRequest.fromPath(path, userId) // <1>
            PutObjectResponse response = objectStorage.upload(request) // <2>
            Optional.ofNullable(response.eTag())
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
