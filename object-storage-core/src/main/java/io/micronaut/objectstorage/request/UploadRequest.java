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
import io.micronaut.http.multipart.CompletedFileUpload;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Object storage upload request.
 * @since 1.0.0
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
    static UploadRequest fromBytes(@NonNull byte[] bytes, @NonNull String key) {
        return new BytesUploadRequest(bytes, key);
    }

    @NonNull
    static UploadRequest fromBytes(@NonNull byte[] bytes,
                                   @NonNull String key,
                                   @NonNull String contentType) {
        return new BytesUploadRequest(bytes, key, contentType);
    }

    @NonNull
    static UploadRequest fromCompletedFileUpload(CompletedFileUpload completedFileUpload) {
        return new CompletedFileUploadRequest(completedFileUpload);
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
     * @return the size of the file, in bytes.
     */
    @NonNull
    Optional<Long> getContentSize();

    /**
     * @return an input stream of the object to be stored.
     */
    @NonNull
    InputStream getInputStream();

}
