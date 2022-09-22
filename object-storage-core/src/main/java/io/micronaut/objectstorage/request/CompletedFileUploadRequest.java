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
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.objectstorage.ObjectStorageException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link UploadRequest} backed by a {@link CompletedFileUpload}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0
 */
public class CompletedFileUploadRequest implements UploadRequest {

    @NonNull
    private final CompletedFileUpload completedFileUpload;
    @NonNull
    private final String key;

    @NonNull
    private Map<String, String> metadata;

    public CompletedFileUploadRequest(@NonNull CompletedFileUpload completedFileUpload) {
        this(completedFileUpload, completedFileUpload.getName(), Collections.emptyMap());
    }

    public CompletedFileUploadRequest(@NonNull CompletedFileUpload completedFileUpload, @NonNull String key) {
        this(completedFileUpload, key, Collections.emptyMap());
    }

    public CompletedFileUploadRequest(@NonNull CompletedFileUpload completedFileUpload, @NonNull String key, @NonNull Map<String, String> metadata) {
        this.completedFileUpload = completedFileUpload;
        this.key = key;
        this.metadata = metadata;
    }

    @NonNull
    @Override
    public Optional<String> getContentType() {
        return completedFileUpload.getContentType()
            .map(MediaType::getName);
    }

    @NonNull
    @Override
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public Optional<Long> getContentSize() {
        return Optional.of(completedFileUpload.getSize());
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        try {
            return completedFileUpload.getInputStream();
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
}
