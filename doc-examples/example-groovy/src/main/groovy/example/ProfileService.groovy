package example

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.ListObjectsResponse
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

    //tag::paginated-listing[]
    void listProfilePicturesByPage(String userId) {
        String prefix = userId + "/" // raw prefix filtering
        String continuation = null
        do {
            ListObjectsRequest request = new ListObjectsRequest(2, prefix, continuation) // <1>
            ListObjectsResponse response = objectStorage.listObjects(request)

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
