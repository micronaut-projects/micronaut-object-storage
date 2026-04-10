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
import java.util.List;
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

    /**
     * @return Whether the backing storage can expose S3 multipart routes.
     */
    default boolean supportsMultipart() {
        return false;
    }

    /**
     * Starts a new multipart upload.
     *
     * @param key the exposed object key
     * @param contentType the requested content type if known
     * @return the initiated multipart upload
     */
    @NonNull
    default S3MultipartUpload createMultipartUpload(@NonNull String key, @Nullable String contentType) {
        throw new UnsupportedOperationException("Multipart uploads are not supported by this bucket");
    }

    /**
     * Stores one multipart upload part.
     *
     * @param key the exposed object key
     * @param uploadId the multipart upload identifier
     * @param partNumber the S3 part number
     * @param inputStream the part bytes
     * @param contentLength the part length if known
     * @return the uploaded part metadata
     */
    @NonNull
    default S3MultipartPart uploadPart(@NonNull String key,
                                       @NonNull String uploadId,
                                       int partNumber,
                                       @NonNull InputStream inputStream,
                                       @Nullable Long contentLength) {
        throw new UnsupportedOperationException("Multipart uploads are not supported by this bucket");
    }

    /**
     * Lists the current uploaded multipart parts.
     *
     * @param key the exposed object key
     * @param uploadId the multipart upload identifier
     * @param partNumberMarker the optional part number cursor
     * @param maxParts the page size
     * @return the multipart listing response
     */
    @NonNull
    default S3MultipartListPartsResponse listParts(@NonNull String key,
                                                   @NonNull String uploadId,
                                                   @Nullable Integer partNumberMarker,
                                                   int maxParts) {
        throw new UnsupportedOperationException("Multipart uploads are not supported by this bucket");
    }

    /**
     * Completes a multipart upload.
     *
     * @param key the exposed object key
     * @param uploadId the multipart upload identifier
     * @param completedParts the uploaded parts in completion order
     * @return the completion response
     */
    @NonNull
    default S3MultipartCompletedUpload completeMultipartUpload(@NonNull String key,
                                                               @NonNull String uploadId,
                                                               @NonNull List<S3CompletedPart> completedParts) {
        throw new UnsupportedOperationException("Multipart uploads are not supported by this bucket");
    }

    /**
     * Aborts a multipart upload.
     *
     * @param key the exposed object key
     * @param uploadId the multipart upload identifier
     */
    default void abortMultipartUpload(@NonNull String key, @NonNull String uploadId) {
        throw new UnsupportedOperationException("Multipart uploads are not supported by this bucket");
    }
}
