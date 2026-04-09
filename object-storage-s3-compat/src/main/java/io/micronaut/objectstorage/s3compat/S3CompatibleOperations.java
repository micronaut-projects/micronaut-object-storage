/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Optional;

/**
 * Internal backend SPI for the S3-compatible transport layer.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public interface S3CompatibleOperations {

    /**
     * @param key the exposed object key
     * @return the object metadata and content if present
     */
    @NonNull
    Optional<S3Object> getObject(@NonNull String key);

    /**
     * Stores an object behind the exposed S3-compatible bucket.
     *
     * @param key the exposed object key
     * @param inputStream the object bytes
     * @param contentLength the object length if known
     * @param contentType the object content type if known
     * @return the storage response
     */
    @NonNull
    UploadResponse<?> putObject(@NonNull String key,
                                @NonNull InputStream inputStream,
                                @Nullable Long contentLength,
                                @Nullable String contentType);

    /**
     * Deletes an object behind the exposed S3-compatible bucket.
     *
     * @param key the exposed object key
     */
    void deleteObject(@NonNull String key);

    /**
     * Lists exposed object keys.
     *
     * @param request the paging request
     * @return the list response
     */
    @NonNull
    S3ListResponse listObjects(@NonNull ListObjectsRequest request);
}
