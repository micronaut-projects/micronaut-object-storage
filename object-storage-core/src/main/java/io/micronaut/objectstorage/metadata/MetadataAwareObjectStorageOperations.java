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
package io.micronaut.objectstorage.metadata;

import io.micronaut.objectstorage.ObjectStorageOperations;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * Metadata-aware object storage workflow API.
 *
 * @param <I> Cloud vendor-specific upload request class or builder.
 * @param <O> Cloud vendor-specific upload response.
 * @param <D> Cloud vendor-specific delete response.
 * @since 1.6.0
 */
public interface MetadataAwareObjectStorageOperations<I, O, D> extends ObjectStorageOperations<I, O, D> {

    /**
     * @return Metadata query operations associated with this storage.
     */
    @NonNull
    ObjectStorageMetadataOperations getMetadataOperations();

    /**
     * @return Registered lifecycle hooks.
     */
    @NonNull
    Collection<ObjectStorageLifecycleHook> getLifecycleHooks();

    /**
     * @return Deterministically ordered lifecycle hooks.
     */
    @NonNull
    default List<ObjectStorageLifecycleHook> getOrderedLifecycleHooks() {
        return ObjectStorageLifecycleHook.ordered(getLifecycleHooks());
    }
}
