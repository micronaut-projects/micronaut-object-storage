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

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main interface for object storage operations.
 *
 * @author Pavol Gressa
 * @since 1.0
 * @param <I> Cloud vendor-specific upload request class or builder.
 * @param <O> Cloud vendor-specific upload response.
 * @param <D> Cloud vendor-specific delete response.
 */
public interface ObjectStorageOperations<I, O, D> {

    /**
     * Uploads an object to the object storage.
     *
     * @param request the upload request
     * @return the upload response
     * @throws ObjectStorageException if there was a failure storing the object
     */
    @Blocking
    @NonNull
    UploadResponse<O> upload(@NonNull UploadRequest request);

    /**
     * Uploads an object to the object storage.
     *
     * @param request the upload request
     * @param requestConsumer Upload request builder consumer
     * @return the upload response
     * @throws ObjectStorageException if there was a failure storing the object
     */
    @Blocking
    @NonNull
    UploadResponse<O> upload(@NonNull UploadRequest request, @NonNull Consumer<I> requestConsumer);

    /**
     * Gets the object from object storage.
     *
     * @param key the object path in the format {@code /foo/bar/file}
     * @return the object, or empty optional if the object does not exist
     * @throws ObjectStorageException if there was a failure retrieving the object
     */
    @Blocking
    @NonNull
    Optional<ObjectStorageEntry<?>> retrieve(@NonNull String key);

    /**
     * Deletes an object from the object storage.
     *
     * @param key object path in the format {@code /foo/bar/file}
     * @return Cloud vendor-specific delete response.
     */
    @Blocking
    @NonNull
    D delete(@NonNull String key);
}
