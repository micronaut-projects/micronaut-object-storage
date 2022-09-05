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
import java.util.Optional;

/**
 * An {@link UploadRequest} backed by a {@link CompletedFileUpload}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0
 */
public class CompletedFileUploadRequest implements UploadRequest {

    private final CompletedFileUpload completedFileUpload;

    public CompletedFileUploadRequest(CompletedFileUpload completedFileUpload) {
        this.completedFileUpload = completedFileUpload;
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
        return completedFileUpload.getName();
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
}
