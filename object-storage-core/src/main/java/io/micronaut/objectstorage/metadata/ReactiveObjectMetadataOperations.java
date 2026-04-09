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
 * Reactive companion contract for object metadata persistence.
 *
 * @param <T> The provider-native metadata representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
public interface ReactiveObjectMetadataOperations<T> {

    /**
     * Retrieves the stored metadata snapshot for an object.
     *
     * @param key The object key.
     * @return A publisher that emits the metadata entry if one exists.
     */
    @NonNull
    Publisher<Optional<ObjectMetadataEntry<T>>> retrieve(@NonNull String key);

    /**
     * Saves a metadata snapshot for an object.
     *
     * @param write The metadata snapshot to persist.
     * @return A completion-only publisher.
     */
    @NonNull
    Publisher<Void> save(@NonNull ObjectMetadataWrite write);

    /**
     * Deletes the metadata snapshot for an object.
     *
     * @param key The object key.
     * @return A completion-only publisher.
     */
    @NonNull
    Publisher<Void> delete(@NonNull String key);

    /**
     * Checks whether a metadata snapshot exists for an object.
     *
     * @param key The object key.
     * @return A publisher that emits {@code true} if metadata exists for the object.
     */
    @NonNull
    Publisher<Boolean> exists(@NonNull String key);
}
