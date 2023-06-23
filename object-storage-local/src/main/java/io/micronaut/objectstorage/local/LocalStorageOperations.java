package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An implementation of {@link ObjectStorageOperations} that uses the local file system. Useful for
 * testing.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@EachBean(LocalStorageConfiguration.class)
public class LocalStorageOperations implements ObjectStorageOperations<LocalStorageOperations.LocalStorageFile, LocalStorageOperations.LocalStorageFile, LocalStorageOperations.LocalStorageFile> {

    private final LocalStorageConfiguration configuration;

    public LocalStorageOperations(@Parameter LocalStorageConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    @NonNull
    public UploadResponse<LocalStorageFile> upload(@NonNull UploadRequest request) {
        return upload(request, localStorageFile -> { });
    }

    @Override
    @NonNull
    public UploadResponse<LocalStorageFile> upload(@NonNull UploadRequest request,
                                                   @NonNull Consumer<LocalStorageFile> requestConsumer) {
        File destination = new File(configuration.getPath().toFile(), request.getKey());
        try (OutputStream out = new FileOutputStream(destination)) {
            request.getInputStream().transferTo(out);
            LocalStorageFile file = new LocalStorageFile(destination.toPath());
            return UploadResponse.of(request.getKey(), "eTag", file);
        } catch (IOException e) {
            throw new ObjectStorageException("Error copying file to: " + destination, e);
        }
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
        public Optional<LocalStorageEntry> retrieve(@NonNull String key) {
        return Optional.empty();
    }

    @Override
    @NonNull
    public LocalStorageFile delete(@NonNull String key) {
        return null;
    }

    @Override
    public boolean exists(@NonNull String key) {
        return ObjectStorageOperations.super.exists(key);
    }

    @Override
    @NonNull
    public Set<String> listObjects() {
        return ObjectStorageOperations.super.listObjects();
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        ObjectStorageOperations.super.copy(sourceKey, destinationKey);
    }

    record LocalStorageFile(Path path) { }
}
