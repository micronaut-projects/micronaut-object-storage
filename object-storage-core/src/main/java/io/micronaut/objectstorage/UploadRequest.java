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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @NonNull
    static UploadRequest fromPath(@NonNull Path path) {
        return new FileUploadRequest(path);
    }

    @NonNull
    static UploadRequest fromPath(@NonNull Path path, String prefix) {
        return new FileUploadRequest(path, prefix);
    }

    @NonNull
    static UploadRequest fromBytes(@NonNull byte[] bytes,
                                   @NonNull String contentType,
                                   @NonNull String key) {
        return new BytesUploadRequest(bytes, contentType, key);
    }

    /**
     * @return the content type of this upload request.
     */
    @NonNull
    Optional<String> getContentType();

    /**
     * @return the file name with path.
     */
    @NonNull
    String getKey();

    /**
     * @return the size of the part, in bytes.
     */
    @NonNull
    Optional<Long> getContentSize();

    /**
     * @return an input stream of the object to be stored.
     */
    @NonNull
    InputStream getInputStream();

    /**
     * Upload request implementation using {@link java.io.File}.
     */
    class FileUploadRequest implements UploadRequest {

        private static final Logger LOG = LoggerFactory.getLogger(FileUploadRequest.class);

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
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error calculating the size of the file.", e);
                }
                return Optional.empty();
            }
        }

        @Override
        @NonNull
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error creating an input stream to read from the file.", e);
                }
                throw new ObjectStorageException(e);
            }
        }
    }

    /**
     * Upload request implementation using byte array.
     */
    class BytesUploadRequest implements UploadRequest {

        @NonNull
        private final byte[] bytes;

        @NonNull
        private final String contentType;

        @NonNull
        private final String key;

        public BytesUploadRequest(@NonNull byte[] bytes,
                                  @NonNull String contentType,
                                  @NonNull String key) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.key = key;
        }

        @Override
        @NonNull
        public Optional<String> getContentType() {
            return Optional.of(contentType);
        }

        @Override
        @NonNull
        public String getKey() {
            return key;
        }

        @Override
        @NonNull
        public Optional<Long> getContentSize() {
            return Optional.of((long) bytes.length);
        }

        @Override
        @NonNull
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
