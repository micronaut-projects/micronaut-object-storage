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

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata snapshot for a logical storage container.
 */
public final class StorageContainerMetadata {

    private final String tenantId;
    private final StorageDescriptor storageDescriptor;
    private final Instant createdAt;
    private final Instant updatedAt;

    public StorageContainerMetadata(@NonNull String tenantId,
                                    @NonNull StorageDescriptor storageDescriptor,
                                    @NonNull Instant createdAt,
                                    @NonNull Instant updatedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.storageDescriptor = Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @NonNull
    public String getTenantId() {
        return tenantId;
    }

    @NonNull
    public StorageDescriptor getStorageDescriptor() {
        return storageDescriptor;
    }

    @NonNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
