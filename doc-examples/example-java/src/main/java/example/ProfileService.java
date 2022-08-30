package example;

import io.micronaut.objectstorage.*;
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

    private final ObjectStorageOperations objectStorage;

    public ProfileService(ObjectStorageOperations objectStorage) {
        this.objectStorage = objectStorage;
    }
//end::beginclass[]

//tag::upload[]
    public Optional<String> saveProfilePicture(String userId, Path path) {
        try {
            UploadRequest request = UploadRequest.fromPath(path, userId);
            UploadResponse response = objectStorage.upload(request);
            return Optional.ofNullable(response.getETag());
        } catch (ObjectStorageException e) {
            return Optional.empty();
        }
    }
//end::upload[]

    public Optional<Path> retrieveProfilePicture(String userId, String fileName) {
        try {
            String key = userId + "/" + fileName;
            Optional<InputStream> stream = objectStorage.retrieve(key)
                .map(ObjectStorageEntry::getInputStream);
            if (stream.isPresent()) {
                Path destination = File.createTempFile(userId, "temp").toPath();
                Files.copy(stream.get(), destination, StandardCopyOption.REPLACE_EXISTING);
                return Optional.of(destination);
            } else {
                return Optional.empty();
            }
        } catch (ObjectStorageException | IOException e) {
            return Optional.empty();
        }
    }

    public void deleteProfilePicture(String userId, String fileName) {
        String key = userId + "/" + fileName;
        objectStorage.delete(key);
    }
//tag::endclass[]
}
//end::endclass[]
