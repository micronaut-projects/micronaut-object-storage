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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Upload request implementation using {@link java.io.File}.
 */
public class FileUploadRequest implements UploadRequest {

    @NonNull
    private final String keyName;

    @Nullable
    private String contentType;

    @NonNull
    private final Path path;

    @NonNull
    private Map<String, String> metadata;

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
        this(prefix != null ? prefix + "/" + keyName : keyName, contentType, localFilePath, Collections.emptyMap());
    }

    public FileUploadRequest(@NonNull String keyName,
                             @Nullable String contentType,
                             @NonNull Path path,
                             @NonNull Map<String, String> metadata) {
        this.keyName = keyName;
        this.contentType = contentType;
        this.path = path;
        this.metadata = metadata;
    }

    /**
     * @return The {@link File} associated with the underlying {@link Path}.
     */
    @NonNull
    public File getFile() {
        return path.toFile();
    }

    /**
     * @return The underlying {@link Path}.
     */
    @NonNull
    public Path getPath() {
        return path;
    }

    /**
     * @return The absolute path of the underlying {@link Path}.
     */
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

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public void setMetadata(@NonNull Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public void setContentType(@NonNull String contentType) {
        this.contentType = contentType;
    }
}
