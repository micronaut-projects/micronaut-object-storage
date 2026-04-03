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
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * API for bucket/container lifecycle management.
 *
 * @param <T> The provider-native bucket/container representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
public interface BucketOperations<T> {
    /**
     * Create a new bucket with the given name.
     *
     * @param name The name of the new bucket
     */
    @Blocking
    void create(@NonNull String name);

    /**
     * Retrieve an existing bucket/container.
     *
     * @param name The name of the bucket
     * @return The provider-native bucket/container data if it exists.
     */
    @Blocking
    @NonNull
    Optional<BucketEntry<T>> retrieve(@NonNull String name);

    /**
     * Delete a bucket/container with the given name.
     *
     * @param name The name of the bucket/container.
     */
    @Blocking
    void delete(@NonNull String name);

    /**
     * Checks whether a bucket/container exists.
     * 
     * @param name The bucket/container name.
     * @return {@code true} if the bucket/container exists.
     */
    @Blocking
    default boolean exists(@NonNull String name) {
        return retrieve(name).isPresent();
    }
}
