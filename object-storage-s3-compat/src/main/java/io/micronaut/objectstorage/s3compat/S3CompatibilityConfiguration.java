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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.EachPropertyContainsEntriesCondition;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Configuration for one exposed S3-compatible bucket.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachProperty(S3CompatibilityConfiguration.PREFIX)
@Requires(condition = EachPropertyContainsEntriesCondition.class)
@Introspected
public class S3CompatibilityConfiguration extends AbstractObjectStorageConfiguration {

    /**
     * Configuration prefix ending.
     */
    public static final String NAME = "s3-compat";

    /**
     * Configuration prefix.
     */
    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    @NonNull
    private String storage;

    @Nullable
    private S3CompatibilityStorageProvider storageProvider;

    @Nullable
    private String basePath;

    public S3CompatibilityConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return The name of the backing object-storage bean.
     */
    @NonNull
    public String getStorage() {
        return storage;
    }

    /**
     * @param storage The backing object-storage bean name.
     */
    public void setStorage(@NonNull String storage) {
        this.storage = storage;
    }

    /**
     * @return The optional backing provider hint used when multiple providers share the same storage bean name.
     */
    @NonNull
    public Optional<S3CompatibilityStorageProvider> getStorageProvider() {
        return Optional.ofNullable(storageProvider);
    }

    /**
     * @param storageProvider The optional backing provider hint.
     */
    public void setStorageProvider(@Nullable S3CompatibilityStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    /**
     * @return The optional internal prefix used for exposed keys.
     */
    @NonNull
    public Optional<String> getBasePath() {
        return Optional.ofNullable(basePath);
    }

    /**
     * @param basePath The internal prefix used for exposed keys.
     */
    public void setBasePath(@Nullable String basePath) {
        this.basePath = normalizeBasePath(basePath);
    }

    /**
     * Whether to enable or disable this S3-compatible bucket.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    private static String normalizeBasePath(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? null : normalized + '/';
    }
}
