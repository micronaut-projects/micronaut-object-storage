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
package io.micronaut.objectstorage.metadata.jdbc.repository;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageObjectMetadata;
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository contract for persisted object metadata rows.
 */
@Internal
public interface StorageObjectMetadataRepository {

    void upsert(@NonNull StorageObjectMetadata metadata, @NonNull Map<String, String> attributes);

    @NonNull
    Optional<StorageObjectMetadataRecord> findByTenantAndObjectHash(@NonNull String tenantId,
                                                                     @NonNull StorageDescriptor storageDescriptor,
                                                                     @NonNull String objectKeyHash);

    @NonNull
    List<StorageObjectMetadataRecord> listByTenantAndQuery(@NonNull String tenantId,
                                                            @NonNull StorageObjectMetadataQuery query);

    void deleteByTenantAndObjectHash(@NonNull String tenantId,
                                     @NonNull StorageDescriptor storageDescriptor,
                                     @NonNull String objectKeyHash);
}
