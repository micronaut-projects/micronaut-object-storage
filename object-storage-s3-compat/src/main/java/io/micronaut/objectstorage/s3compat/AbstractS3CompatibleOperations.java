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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Base class for S3-compatible backend adapters that expose one configured bucket.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
abstract class AbstractS3CompatibleOperations implements S3CompatibleOperations {

    private final S3CompatibilityConfiguration configuration;

    AbstractS3CompatibleOperations(@NonNull S3CompatibilityConfiguration configuration) {
        this.configuration = configuration;
    }

    @NonNull
    protected final String resolveStorageKey(@NonNull String key) {
        String normalizedKey = normalizeKey(key);
        return configuration.getBasePath()
            .map(basePath -> basePath + normalizedKey)
            .orElse(normalizedKey);
    }

    @Nullable
    protected final String resolveListPrefix(@Nullable String prefix) {
        String normalizedPrefix = prefix == null ? null : normalizeKey(prefix);
        if (normalizedPrefix == null || normalizedPrefix.isEmpty()) {
            return configuration.getBasePath().orElse(null);
        }
        return configuration.getBasePath()
            .map(basePath -> basePath + normalizedPrefix)
            .orElse(normalizedPrefix);
    }

    @NonNull
    protected final String stripBasePath(@NonNull String key) {
        return configuration.getBasePath()
            .filter(key::startsWith)
            .map(basePath -> key.substring(basePath.length()))
            .orElse(key);
    }

    @NonNull
    private static String normalizeKey(@NonNull String key) {
        String normalized = key;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
