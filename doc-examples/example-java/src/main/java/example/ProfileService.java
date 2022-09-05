package example;

import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

//tag::beginclass[]
@Singleton
public class ProfileService {

    private final ObjectStorageOperations<?, ?, ?> objectStorage;

    public ProfileService(ObjectStorageOperations<?, ?, ?> objectStorage) {
        this.objectStorage = objectStorage;
    }
//end::beginclass[]

    //tag::upload[]
    public String saveProfilePicture(String userId, Path path) {
        UploadRequest request = UploadRequest.fromPath(path, userId); // <1>
        UploadResponse<?> response = objectStorage.upload(request); // <2>
        return response.getKey(); // <3>
    }
    //end::upload[]

    //tag::retrieve[]
    public Optional<Path> retrieveProfilePicture(String userId, String fileName) {
        try {
            String key = userId + "/" + fileName;
            Optional<InputStream> stream = objectStorage.retrieve(key) // <1>
                .map(ObjectStorageEntry::getInputStream);
            if (stream.isPresent()) {
                Path destination = File.createTempFile(userId, "temp").toPath();
                Files.copy(stream.get(), destination, StandardCopyOption.REPLACE_EXISTING);
                return Optional.of(destination);
            } else {
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }
    //end::retrieve[]

    //tag::delete[]
    public void deleteProfilePicture(String userId, String fileName) {
        String key = userId + "/" + fileName;
        objectStorage.delete(key); // <1>
    }
    //end::delete[]

//tag::endclass[]
}
//end::endclass[]
