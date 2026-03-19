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
package io.micronaut.objectstorage.metadata.jdbc;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState;
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations;
import io.micronaut.objectstorage.metadata.StorageContainerMetadata;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageObjectMetadata;
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery;
import io.micronaut.objectstorage.metadata.TenantResolver;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Internal
final class DefaultObjectStorageMetadataOperations implements ObjectStorageMetadataOperations {

    private static final Map<ObjectMetadataReconciliationState, Boolean> VISIBLE_STATES = Map.of(
        ObjectMetadataReconciliationState.SUCCEEDED, true,
        ObjectMetadataReconciliationState.PENDING, false,
        ObjectMetadataReconciliationState.PROCESSING, false,
        ObjectMetadataReconciliationState.FAILED, false,
        ObjectMetadataReconciliationState.DEAD_LETTER, false
    );

    private final TenantResolver tenantResolver;
    private final StorageContainerMetadataRepository containerRepository;
    private final StorageObjectMetadataRepository objectRepository;

    @Deprecated(forRemoval = true)
    DefaultObjectStorageMetadataOperations(StorageDescriptor storageDescriptor,
                                           TenantResolver tenantResolver,
                                           StorageContainerMetadataRepository containerRepository,
                                           StorageObjectMetadataRepository objectRepository) {
        this(tenantResolver, containerRepository, objectRepository);
        Objects.requireNonNull(storageDescriptor, "storageDescriptor");
    }

    DefaultObjectStorageMetadataOperations(TenantResolver tenantResolver,
                                           StorageContainerMetadataRepository containerRepository,
                                           StorageObjectMetadataRepository objectRepository) {
        this.tenantResolver = tenantResolver;
        this.containerRepository = containerRepository;
        this.objectRepository = objectRepository;
    }

    @Override
    public @NonNull TenantResolver getTenantResolver() {
        return tenantResolver;
    }

    @Override
    public @NonNull Optional<StorageContainerMetadata> findContainer(@NonNull StorageDescriptor storageDescriptor) {
        return containerRepository.findByTenantAndDescriptor(getActiveTenantId(), storageDescriptor)
            .map(StorageContainerMetadataRecord::metadata);
    }

    @Override
    public @NonNull Optional<StorageObjectMetadata> findObject(@NonNull StorageDescriptor storageDescriptor, @NonNull String objectKey) {
        return objectRepository.findByTenantAndObjectHash(getActiveTenantId(), storageDescriptor, StorageObjectMetadata.deterministicObjectKeyHash(objectKey))
            .filter(this::isVisible)
            .map(StorageObjectMetadataRecord::metadata);
    }

    @Override
    public @NonNull List<StorageObjectMetadata> listObjects(@NonNull StorageObjectMetadataQuery query) {
        return objectRepository.listByTenantAndQuery(getActiveTenantId(), query).stream()
            .filter(this::isVisible)
            .map(StorageObjectMetadataRecord::metadata)
            .toList();
    }

    private boolean isVisible(StorageObjectMetadataRecord record) {
        return isVisible(record.metadata());
    }

    private boolean isVisible(StorageObjectMetadata metadata) {
        return VISIBLE_STATES.getOrDefault(metadata.getSyncStatus().getReconciliationState(), false);
    }
}
