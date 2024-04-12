/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.objectstorage.bucket;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageOperations;

import java.util.Set;

/**
 * API for creating, listing, updating, and deleting buckets.
 *
 * @param <I> See {@link ObjectStorageOperations}
 * @param <O> See {@link ObjectStorageOperations}
 * @param <D> See {@link ObjectStorageOperations}
 * @since 2.2.0
 * @author Jonas Konrad
 */
public interface BucketOperations<I, O, D> {
    /**
     * Create a new bucket with the given name.
     *
     * @param name The name of the new bucket
     */
    @Blocking
    @NonNull
    void createBucket(String name);

    /**
     * Delete a new bucket with the given name.
     *
     * @param name The name of the bucket
     */
    @Blocking
    @NonNull
    void deleteBucket(String name);

    /**
     * List the available buckets. This operation may be blocking.
     *
     * @return The available buckets
     */
    @Blocking
    @NonNull
    Set<String> listBuckets();

    /**
     * Create an {@link ObjectStorageOperations} implementation to access data in the given bucket.
     * <br>
     * If the bucket does not exist, this operation <i>may</i> fail, but usually it will only fail
     * when trying to access the bucket contents.
     *
     * @param bucket The bucket name as returned by {@link #listBuckets()}
     * @return An {@link ObjectStorageOperations} for the given bucket
     */
    @NonNull
    ObjectStorageOperations<I, O, D> storageForBucket(@NonNull String bucket);
}
