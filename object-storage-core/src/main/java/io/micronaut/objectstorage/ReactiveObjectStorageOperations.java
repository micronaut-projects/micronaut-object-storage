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
package io.micronaut.objectstorage;

import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reactive companion interface for object storage operations.
 *
 * <p>The phase-1 reactive API mirrors {@link ObjectStorageOperations} while preserving the existing
 * {@link UploadRequest} and {@link ObjectStorageEntry} payload abstractions, which are still
 * {@link java.io.InputStream}-based.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 * @param <I> Cloud vendor-specific upload request class or builder.
 * @param <O> Cloud vendor-specific upload response.
 * @param <D> Cloud vendor-specific delete response.
 */
public interface ReactiveObjectStorageOperations<I, O, D> {

    /**
     * Uploads an object to the object storage. If there is an existing entry, it will be updated.
     *
     * @param request the upload request
     * @return a publisher that emits the upload response
     * @throws ObjectStorageException if there was a failure storing the object
     */
    @NonNull
    Publisher<UploadResponse<O>> upload(@NonNull UploadRequest request);

    /**
     * Uploads an object to the object storage. If there is an existing entry, it will be updated.
     *
     * @param request the upload request
     * @param requestConsumer upload request builder consumer
     * @return a publisher that emits the upload response
     * @throws ObjectStorageException if there was a failure storing the object
     */
    @NonNull
    Publisher<UploadResponse<O>> upload(@NonNull UploadRequest request, @NonNull Consumer<I> requestConsumer);

    /**
     * Gets the object from object storage.
     *
     * @param key the object path in the format {@code /foo/bar/file}
     * @param <E> an instance of {@link ObjectStorageEntry}
     * @return a publisher that emits an optional object storage entry
     * @throws ObjectStorageException if there was a failure retrieving the object
     */
    @NonNull
    <E extends ObjectStorageEntry<?>> Publisher<Optional<E>> retrieve(@NonNull String key);

    /**
     * Deletes an object from the object storage.
     *
     * @param key object path in the format {@code /foo/bar/file}
     * @return a publisher that emits the cloud vendor-specific delete response
     * @throws ObjectStorageException if there was a failure deleting the object
     */
    @NonNull
    Publisher<D> delete(@NonNull String key);

    /**
     * Checks whether an entry with the given key exists in the object storage.
     *
     * @param key object path in the format {@code /foo/bar/file}
     * @return a publisher that emits true if the entry exists, false otherwise
     */
    @NonNull
    Publisher<Boolean> exists(@NonNull String key);

    /**
     * Lists the objects that exist in the object storage.
     *
     * @return a publisher that emits the full set of keys
     */
    @NonNull
    Publisher<Set<String>> listObjects();

    /**
     * Lists a page of objects that exist in the object storage.
     *
     * @param request the paginated listing request
     * @return a publisher that emits the current page of object keys
     */
    @NonNull
    Publisher<ListObjectsResponse> listObjects(@NonNull ListObjectsRequest request);

    /**
     * Copies an object stored at <code>sourceKey</code> to <code>destinationKey</code>, within the
     * same object storage (bucket/container). If the destination exists, it will be overwritten.
     *
     * @param sourceKey the key of the source object
     * @param destinationKey the key of the destination object
     * @return a completion-only publisher
     */
    @NonNull
    Publisher<Void> copy(@NonNull String sourceKey, @NonNull String destinationKey);
}
