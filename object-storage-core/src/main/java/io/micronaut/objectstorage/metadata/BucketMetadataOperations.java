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
package io.micronaut.objectstorage.metadata;

import io.micronaut.core.annotation.Blocking;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Portable metadata persistence contract for buckets and containers.
 *
 * <p>{@link #save(BucketMetadataWrite)} uses replace/upsert semantics for the portable metadata
 * snapshot. {@link #delete(String)} is idempotent.</p>
 *
 * @param <T> The provider-native metadata representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
public interface BucketMetadataOperations<T> {

    /**
     * Retrieves the stored metadata snapshot for a bucket or container.
     *
     * @param name The bucket or container name.
     * @return The metadata entry, or {@link Optional#empty()} if no metadata snapshot exists.
     */
    @Blocking
    @NonNull
    Optional<BucketMetadataEntry<T>> retrieve(@NonNull String name);

    /**
     * Saves a metadata snapshot for a bucket or container.
     *
     * @param write The metadata snapshot to persist.
     */
    @Blocking
    void save(@NonNull BucketMetadataWrite write);

    /**
     * Deletes the metadata snapshot for a bucket or container.
     *
     * @param name The bucket or container name.
     */
    @Blocking
    void delete(@NonNull String name);

    /**
     * Checks whether a metadata snapshot exists for a bucket or container.
     *
     * @param name The bucket or container name.
     * @return {@code true} if metadata exists for the bucket or container.
     */
    @Blocking
    default boolean exists(@NonNull String name) {
        return retrieve(name).isPresent();
    }
}
