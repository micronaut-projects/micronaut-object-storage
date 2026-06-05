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
package io.micronaut.objectstorage.multipart;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import org.jspecify.annotations.NonNull;

/**
 * Portable multipart upload lifecycle operations.
 *
 * <p>This contract is additive to {@link ObjectStorageOperations}. Providers may implement it
 * independently without affecting existing single-object upload consumers.</p>
 *
 * <p>Multipart upload handles are caller-owned resources until they are completed or aborted.
 * Implementations must treat repeated abort requests as safe. Completing an upload requires an
 * ordered part manifest, and retries are only safe when the same upload id and exact manifest are
 * reused.</p>
 *
 * @param <C> Cloud vendor-specific create multipart upload response
 * @param <U> Cloud vendor-specific upload part response
 * @param <R> Cloud vendor-specific complete multipart upload response
 * @since 3.1.0
 */
public interface MultipartObjectStorageOperations<C, U, R> {

    /**
     * Creates a new multipart upload and returns the opaque upload handle.
     *
     * @param request the create request
     * @return the multipart upload handle and native response
     * @throws ObjectStorageException if the multipart upload could not be created
     */
    @Blocking
    @NonNull
    CreateMultipartUploadResponse<C> createMultipartUpload(@NonNull CreateMultipartUploadRequest request);

    /**
     * Uploads a single part for an existing multipart upload.
     *
     * @param request the part upload request
     * @return the uploaded part metadata and native response
     * @throws ObjectStorageException if the part upload failed
     */
    @Blocking
    @NonNull
    UploadPartResponse<U> uploadPart(@NonNull UploadPartRequest request);

    /**
     * Lists uploaded parts for an in-progress multipart upload.
     *
     * @param request the list request
     * @return the ordered parts in the current page and an optional continuation token
     * @throws ObjectStorageException if listing fails
     */
    @Blocking
    @NonNull
    ListMultipartPartsResponse listParts(@NonNull ListMultipartPartsRequest request);

    /**
     * Completes a multipart upload using the provided ordered part manifest.
     *
     * @param request the complete request
     * @return the completed upload response and native response
     * @throws ObjectStorageException if completion fails
     */
    @Blocking
    @NonNull
    CompleteMultipartUploadResponse<R> completeMultipartUpload(@NonNull CompleteMultipartUploadRequest request);

    /**
     * Aborts an in-progress multipart upload.
     *
     * <p>This operation must be safe to retry.</p>
     *
     * @param request the abort request
     * @throws ObjectStorageException if abort fails unexpectedly
     */
    @Blocking
    void abortMultipartUpload(@NonNull AbortMultipartUploadRequest request);
}
