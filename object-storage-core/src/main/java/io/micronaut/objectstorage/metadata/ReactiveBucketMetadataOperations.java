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

import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Reactive companion contract for bucket and container metadata persistence.
 *
 * @param <T> The provider-native metadata representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
public interface ReactiveBucketMetadataOperations<T> {

    /**
     * Retrieves the stored metadata snapshot for a bucket or container.
     *
     * @param name The bucket or container name.
     * @return A publisher that emits the metadata entry if one exists.
     */
    @NonNull
    Publisher<Optional<BucketMetadataEntry<T>>> retrieve(@NonNull String name);

    /**
     * Saves a metadata snapshot for a bucket or container.
     *
     * @param write The metadata snapshot to persist.
     * @return A completion-only publisher.
     */
    @NonNull
    Publisher<Void> save(@NonNull BucketMetadataWrite write);

    /**
     * Deletes the metadata snapshot for a bucket or container.
     *
     * @param name The bucket or container name.
     * @return A completion-only publisher.
     */
    @NonNull
    Publisher<Void> delete(@NonNull String name);

    /**
     * Checks whether a metadata snapshot exists for a bucket or container.
     *
     * @param name The bucket or container name.
     * @return A publisher that emits {@code true} if metadata exists for the bucket or container.
     */
    @NonNull
    Publisher<Boolean> exists(@NonNull String name);
}
