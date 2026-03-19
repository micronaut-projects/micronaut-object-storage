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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations;
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook;
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations;
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver;
import io.micronaut.objectstorage.metadata.TenantResolver;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository;

import java.util.List;

@Factory
@Requires(property = ObjectStorageMetadataJdbcConfiguration.PREFIX + ".enabled", notEquals = "false", defaultValue = "true")
final class ObjectStorageMetadataJdbcFactory {

    @EachBean(StorageDescriptorResolver.class)
    ObjectStorageMetadataOperations objectStorageMetadataOperations(StorageDescriptorResolver storageDescriptorResolver,
                                                                    TenantResolver tenantResolver,
                                                                    StorageContainerMetadataRepository containerRepository,
                                                                    StorageObjectMetadataRepository objectRepository) {
        return new DefaultObjectStorageMetadataOperations(
            tenantResolver,
            containerRepository,
            objectRepository
        );
    }

    @EachBean(ObjectStorageMetadataOperations.class)
    <I, O, D> MetadataAwareObjectStorageOperations<I, O, D> metadataAwareObjectStorageOperations(@Parameter ObjectStorageOperations<I, O, D> delegate,
                                                                                                  @Parameter StorageDescriptorResolver storageDescriptorResolver,
                                                                                                  ObjectStorageMetadataOperations metadataOperations,
                                                                                                  TenantResolver tenantResolver,
                                                                                                  StorageContainerMetadataRepository containerRepository,
                                                                                                  StorageMetadataOutboxRepository outboxRepository,
                                                                                                  List<ObjectStorageLifecycleHook> lifecycleHooks) {
        return new DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            storageDescriptorResolver.resolve(),
            tenantResolver,
            containerRepository,
            outboxRepository,
            lifecycleHooks
        );
    }
}
