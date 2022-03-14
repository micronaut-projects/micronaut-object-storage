package io.micronaut.objectstorage;


import io.micronaut.core.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public interface UploadRequest {

    static UploadRequest fromFile(Path path){
        return new FileUploadRequest(path);
    }

    /**
     * Gets the content type of this upload request.
     *
     * @return The content type of this upload request.
     */
    Optional<String> getContentType();

    /**
     * Gets the file name with path.
     *
     * @return The file name with path on object storage.
     */
    String getKey();

    /**
     * Returns the size of the part.
     *
     * @return The size of this part, in bytes.
     */
    Optional<Long> getContentSize();


    InputStream getInputStream();

    boolean isOverwrite();

    class FileUploadRequest implements UploadRequest {

        private final String keyName;
        private final String contentType;
        private final Path file;
        private final boolean overwrite = false;

        public FileUploadRequest(Path localFilePath) {
            this(localFilePath, localFilePath.getFileName().toString(), null, URLConnection.guessContentTypeFromName(localFilePath.toFile().getName()));
        }

        public FileUploadRequest(Path localFilePath, String objectStoragePath) {
            this(localFilePath, localFilePath.toFile().getName(), objectStoragePath, URLConnection.guessContentTypeFromName(localFilePath.toFile().getName()));
        }

        public FileUploadRequest(Path localFilePath, @Nullable String keyName, @Nullable String objectStoragePath, @Nullable String contentType) {
            this.keyName = objectStoragePath != null ? objectStoragePath + "/" + keyName : keyName;
            this.contentType = contentType;
            this.file = localFilePath;
        }

        public File getFile() {
            return file.toFile();
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.ofNullable(contentType);
        }

        @Override
        public String getKey() {
            return keyName;
        }

        @Override
        public Optional<Long> getContentSize() {
            try {
                return Optional.of(Files.size(file));
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        @Override
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public boolean isOverwrite() {
            return overwrite;
        }
    }
}
