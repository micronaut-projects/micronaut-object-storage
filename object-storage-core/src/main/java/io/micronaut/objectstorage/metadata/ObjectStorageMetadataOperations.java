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

import io.micronaut.core.annotation.Blocking;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Query API for object storage metadata.
 *
 * Implementations must scope all methods to the active tenant resolved by
 * {@link #getTenantResolver()}.
 *
 * @since 1.6.0
 */
public interface ObjectStorageMetadataOperations {

    /**
     * @return Resolver used for active tenant scoping.
     */
    @NonNull
    TenantResolver getTenantResolver();

    /**
     * @return Active tenant id used for default query scoping.
     */
    @NonNull
    default String getActiveTenantId() {
        return getTenantResolver().resolveTenantId();
    }

    /**
     * Finds storage container metadata for the active tenant.
     *
     * @param storageDescriptor Storage descriptor.
     * @return Container metadata if present.
     */
    @Blocking
    @NonNull
    Optional<StorageContainerMetadata> findContainer(@NonNull StorageDescriptor storageDescriptor);

    /**
     * Finds object metadata for the active tenant.
     *
     * @param storageDescriptor Storage descriptor.
     * @param objectKey Object key.
     * @return Object metadata if present.
     */
    @Blocking
    @NonNull
    Optional<StorageObjectMetadata> findObject(@NonNull StorageDescriptor storageDescriptor,
                                               @NonNull String objectKey);

    /**
     * Lists object metadata for the active tenant.
     *
     * @param query Query filter.
     * @return Matching metadata entries.
     */
    @Blocking
    @NonNull
    List<StorageObjectMetadata> listObjects(@NonNull StorageObjectMetadataQuery query);
}
