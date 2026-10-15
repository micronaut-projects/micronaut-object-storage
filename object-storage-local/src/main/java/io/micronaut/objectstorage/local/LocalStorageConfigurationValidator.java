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
package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Validates provider and bucket namespaces before local storage is used.
 */
@Context
@Requires(beans = LocalStorageConfiguration.class)
final class LocalStorageConfigurationValidator {

    LocalStorageConfigurationValidator(List<LocalStorageConfiguration> configurations) {
        List<LocalStorageConfiguration> enabled = configurations.stream().filter(LocalStorageConfiguration::isEnabled).toList();
        for (LocalStorageConfiguration configuration : enabled) {
            Path bucket = configuration.getPath().toAbsolutePath().normalize();
            Path root = bucket.getParent();
            if (root == null) {
                continue;
            }
            for (LocalStorageConfiguration other : enabled) {
                Path otherBucket = other.getPath().toAbsolutePath().normalize();
                if (!sameRoot(root, otherBucket.getParent())) {
                    continue;
                }
                if (!configuration.getInternalDirectory().equals(other.getInternalDirectory())) {
                    throw new ConfigurationException("Local storage configurations '" + configuration.getName() + "' and '" + other.getName()
                        + "' share a storage root but use different internal-directory values");
                }
                if (configuration.getInternalDirectory().equalsIgnoreCase(otherBucket.getFileName().toString())) {
                    throw new ConfigurationException("Local storage internal-directory for '" + configuration.getName()
                        + "' collides with configured bucket '" + other.getName() + "': " + otherBucket);
                }
            }
        }
        // Inspect custom layouts only after all configuration conflicts have been rejected.
        enabled.forEach(LocalStorageLayout::new);
    }

    private static boolean sameRoot(Path first, @Nullable Path second) {
        if (first.equals(second)) {
            return true;
        }
        try {
            return second != null && Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
        } catch (IOException e) {
            throw new ConfigurationException("Cannot compare local storage roots", e);
        }
    }
}
