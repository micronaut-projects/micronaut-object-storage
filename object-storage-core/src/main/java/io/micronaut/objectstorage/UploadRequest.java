/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Object storage upload request.
 */
public interface UploadRequest {

    static UploadRequest fromFile(@NonNull Path path) {
        return new FileUploadRequest(path);
    }

    static UploadRequest fromFile(@NonNull Path path, String key) {
        return new FileUploadRequest(path, key);
    }

    static UploadRequest fromBytes(@NonNull byte[] bytes,
                                   @NonNull String contentType,
                                   @NonNull String key) {
        return new BytesUploadRequest(bytes, contentType, key);
    }

    /**
     * @return the content type of this upload request.
     */
    Optional<String> getContentType();

    /**
     * @return the file name with path.
     */
    String getKey();

    /**
     * @return the size of the part, in bytes.
     */
    Optional<Long> getContentSize();

    /**
     * @return an input stream of the object to be stored.
     */
    InputStream getInputStream();

    /**
     * Upload request implementation using {@link java.io.File}.
     */
    class FileUploadRequest implements UploadRequest {

        private final String keyName;
        private final String contentType;
        private final Path path;

        public FileUploadRequest(Path localFilePath) {
            this(localFilePath, localFilePath.getFileName().toString(), null,
                URLConnection.guessContentTypeFromName(localFilePath.toFile().getName()));
        }

        public FileUploadRequest(Path localFilePath, String objectStoragePath) {
            this(localFilePath, localFilePath.toFile().getName(), objectStoragePath,
                URLConnection.guessContentTypeFromName(localFilePath.toFile().getName()));
        }

        public FileUploadRequest(Path localFilePath,
                                 @Nullable String keyName,
                                 @Nullable String objectStoragePath,
                                 @Nullable String contentType) {
            this.keyName = objectStoragePath != null ? objectStoragePath + '/' + keyName : keyName;
            this.contentType = contentType;
            this.path = localFilePath;
        }

        public File getFile() {
            return path.toFile();
        }

        public Path getPath() {
            return path;
        }

        public String getAbsolutePath() {
            return path.toAbsolutePath().toString();
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
                return Optional.of(Files.size(path));
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        @Override
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                throw new ObjectStorageException(e);
            }
        }
    }

    /**
     * Upload request implementation using byte array.
     */
    class BytesUploadRequest implements UploadRequest {

        private final byte[] bytes;
        private final String contentType;
        private final String key;

        public BytesUploadRequest(@NonNull byte[] bytes,
                                  @NonNull String contentType,
                                  @NonNull String key) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.key = key;
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.of(contentType);
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Optional<Long> getContentSize() {
            return Optional.of((long) bytes.length);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
