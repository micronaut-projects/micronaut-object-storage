package example;

import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

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

    private final ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> objectStorage;

    public ProfileService(ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> objectStorage) {
        this.objectStorage = objectStorage;
    }
//end::beginclass[]

    //tag::upload[]
    public Optional<String> saveProfilePicture(String userId, Path path) {
        try {
            UploadRequest request = UploadRequest.fromPath(path, userId); // <1>
            PutObjectResponse response = objectStorage.upload(request); // <2>
            return Optional.ofNullable(response.eTag());
        } catch (ObjectStorageException e) {
            return Optional.empty();
        }
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
        } catch (ObjectStorageException | IOException e) {
            return Optional.empty();
        }
    }
    //end::retrieve[]

    //tag::delete[]
    public void deleteProfilePicture(String userId, String fileName) {
        String key = userId + "/" + fileName;
        objectStorage.delete(key);
    }
    //end::delete[]

//tag::endclass[]
}
//end::endclass[]
