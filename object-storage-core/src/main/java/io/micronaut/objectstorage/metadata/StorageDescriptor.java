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
 * Descriptor identifying a logical storage binding and provider target.
 */
public final class StorageDescriptor {

    private final String storageName;
    private final String providerId;
    private final String logicalContainer;
    @Nullable
    private final String providerNamespace;
    private final String providerContainer;

    public StorageDescriptor(@NonNull String storageName,
                             @NonNull String providerId,
                             @NonNull String logicalContainer,
                             @Nullable String providerNamespace,
                             @NonNull String providerContainer) {
        this.storageName = Objects.requireNonNull(storageName, "storageName");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.logicalContainer = Objects.requireNonNull(logicalContainer, "logicalContainer");
        this.providerNamespace = providerNamespace;
        this.providerContainer = Objects.requireNonNull(providerContainer, "providerContainer");
    }

    @NonNull
    public String getStorageName() {
        return storageName;
    }

    @NonNull
    public String getProviderId() {
        return providerId;
    }

    @NonNull
    public String getLogicalContainer() {
        return logicalContainer;
    }

    @NonNull
    public Optional<String> getProviderNamespace() {
        return Optional.ofNullable(providerNamespace);
    }

    @NonNull
    public String getProviderContainer() {
        return providerContainer;
    }

    @NonNull
    public String getProviderContainerIdentity() {
        return getProviderNamespace().map(namespace -> namespace + "/" + providerContainer).orElse(providerContainer);
    }
}
