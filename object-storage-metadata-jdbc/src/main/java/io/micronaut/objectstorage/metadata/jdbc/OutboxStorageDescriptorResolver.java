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
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@Internal
final class OutboxStorageDescriptorResolver {

    private final Map<Key, StorageDescriptor> descriptors;

    OutboxStorageDescriptorResolver(Collection<StorageDescriptorResolver> resolvers) {
        this.descriptors = Objects.requireNonNull(resolvers, "resolvers")
            .stream()
            .map(StorageDescriptorResolver::resolve)
            .collect(Collectors.toUnmodifiableMap(
                descriptor -> new Key(descriptor.getStorageName(), descriptor.getLogicalContainer()),
                Function.identity(),
                (left, right) -> left
            ));
    }

    @NonNull
    Optional<StorageDescriptor> resolve(@NonNull String storageName,
                                        @NonNull String logicalContainer) {
        return Optional.ofNullable(descriptors.get(new Key(storageName, logicalContainer)));
    }

    @NonNull
    StorageDescriptor resolveOrFallback(@NonNull String storageName,
                                        @NonNull String logicalContainer) {
        return resolve(storageName, logicalContainer)
            .orElseGet(() -> new StorageDescriptor(storageName, "unknown", logicalContainer, null, logicalContainer));
    }

    private record Key(String storageName, String logicalContainer) {
    }
}
