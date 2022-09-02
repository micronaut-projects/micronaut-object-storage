package io.micronaut.objectstorage.request;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.objectstorage.ObjectStorageException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Upload request implementation using {@link java.io.File}.
 */
public class FileUploadRequest implements UploadRequest {
    private final String keyName;
    private final String contentType;
    private final Path path;

    public FileUploadRequest(@NonNull Path localFilePath) {
        this(localFilePath, localFilePath.getFileName().toString(), null,
            URLConnection.guessContentTypeFromName(localFilePath.toFile().getName()));
    }

    public FileUploadRequest(@NonNull Path localFilePath,
                             @Nullable String prefix) {
        this(localFilePath, localFilePath.toFile().getName(), prefix,
            URLConnection.guessContentTypeFromName(localFilePath.toFile().getName()));
    }

    public FileUploadRequest(@NonNull Path localFilePath,
                             @NonNull String keyName,
                             @Nullable String prefix,
                             @Nullable String contentType) {
        this.keyName = prefix != null ? prefix + "/" + keyName : keyName;
        this.contentType = contentType;
        this.path = localFilePath;
    }

    @NonNull
    public File getFile() {
        return path.toFile();
    }

    @NonNull
    public Path getPath() {
        return path;
    }

    @NonNull
    public String getAbsolutePath() {
        return path.toAbsolutePath().toString();
    }

    @Override
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    @NonNull
    public String getKey() {
        return keyName;
    }

    @Override
    @NonNull
    public Optional<Long> getContentSize() {
        try {
            return Optional.of(Files.size(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new ObjectStorageException(e);
        }
    }
}
