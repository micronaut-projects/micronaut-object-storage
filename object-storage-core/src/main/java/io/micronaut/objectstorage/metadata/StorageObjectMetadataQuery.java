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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Query filter for object metadata listing.
 *
 * Tenant scoping is intentionally resolved from {@link TenantResolver} by
 * {@link ObjectStorageMetadataOperations} implementations.
 *
 * @since 1.6.0
 */
public final class StorageObjectMetadataQuery {

    @Nullable
    private final String storageName;
    @Nullable
    private final String logicalContainer;
    @Nullable
    private final String objectKeyPrefix;

    public StorageObjectMetadataQuery(@Nullable String storageName,
                                      @Nullable String logicalContainer,
                                      @Nullable String objectKeyPrefix) {
        this.storageName = storageName;
        this.logicalContainer = logicalContainer;
        this.objectKeyPrefix = objectKeyPrefix;
    }

    @NonNull
    public static StorageObjectMetadataQuery all() {
        return new StorageObjectMetadataQuery(null, null, null);
    }

    @NonNull
    public StorageObjectMetadataQuery withStorageName(@Nullable String storageName) {
        return new StorageObjectMetadataQuery(storageName, logicalContainer, objectKeyPrefix);
    }

    @NonNull
    public StorageObjectMetadataQuery withLogicalContainer(@Nullable String logicalContainer) {
        return new StorageObjectMetadataQuery(storageName, logicalContainer, objectKeyPrefix);
    }

    @NonNull
    public StorageObjectMetadataQuery withObjectKeyPrefix(@Nullable String objectKeyPrefix) {
        return new StorageObjectMetadataQuery(storageName, logicalContainer, objectKeyPrefix);
    }

    @NonNull
    public Optional<String> getStorageName() {
        return Optional.ofNullable(storageName);
    }

    @NonNull
    public Optional<String> getLogicalContainer() {
        return Optional.ofNullable(logicalContainer);
    }

    @NonNull
    public Optional<String> getObjectKeyPrefix() {
        return Optional.ofNullable(objectKeyPrefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StorageObjectMetadataQuery that = (StorageObjectMetadataQuery) o;
        return Objects.equals(storageName, that.storageName)
            && Objects.equals(logicalContainer, that.logicalContainer)
            && Objects.equals(objectKeyPrefix, that.objectKeyPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageName, logicalContainer, objectKeyPrefix);
    }
}
