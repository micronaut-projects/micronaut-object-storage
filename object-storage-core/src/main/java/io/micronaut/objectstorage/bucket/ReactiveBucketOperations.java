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
package io.micronaut.objectstorage.bucket;

import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Reactive companion API for bucket/container lifecycle management.
 *
 * @param <T> The provider-native bucket/container representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
public interface ReactiveBucketOperations<T> {

    /**
     * Create a new bucket/container with the given name.
     *
     * @param name The bucket/container name.
     * @return a completion-only publisher.
     */
    @NonNull
    Publisher<Void> create(@NonNull String name);

    /**
     * Retrieve an existing bucket/container.
     *
     * @param name The bucket/container name.
     * @return a publisher that emits the provider-native bucket/container data if it exists.
     */
    @NonNull
    Publisher<Optional<BucketEntry<T>>> retrieve(@NonNull String name);

    /**
     * Delete a bucket/container with the given name.
     *
     * @param name The bucket/container name.
     * @return a completion-only publisher.
     */
    @NonNull
    Publisher<Void> delete(@NonNull String name);

    /**
     * Checks whether a bucket/container exists.
     *
     * @param name The bucket/container name.
     * @return a publisher that emits {@code true} if the bucket/container exists.
     */
    @NonNull
    Publisher<Boolean> exists(@NonNull String name);
}
