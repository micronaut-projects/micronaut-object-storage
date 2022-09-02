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
import io.micronaut.objectstorage.request.UploadRequest;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main interface for object storage operations.
 *
 * @author Pavol Gressa
 * @since 1.0
 * @param <I> Cloud vendor-specific upload request.
 * @param <O> Cloud vendor-specific upload response.
 */
public interface ObjectStorageOperations<I, O> {

    /**
     * Uploads an object to the object storage.
     *
     * @param request the upload request
     * @return the upload response
     * @throws ObjectStorageException if there was a failure storing the object
     */
    @NonNull
    O upload(@NonNull UploadRequest request);

    /**
     * Uploads an object to the object storage.
     *
     * @param request the upload request
     * @param requestConsumer Upload request builder consumer
     * @return the upload response
     * @throws ObjectStorageException if there was a failure storing the object
     */
    @NonNull
    O upload(@NonNull UploadRequest request, @NonNull Consumer<I> requestConsumer);

    /**
     * Gets the object from object storage.
     *
     * @param key the object path in the format {@code /foo/bar/file}
     * @return the object, or empty optional if the object does not exist
     * @throws ObjectStorageException if there was a failure retrieving the object
     */
    @NonNull
    Optional<ObjectStorageEntry> retrieve(@NonNull String key);

    /**
     * Deletes an object from the object storage.
     *
     * @param key object path in the format {@code /foo/bar/file}
     */
    void delete(@NonNull String key);
}
