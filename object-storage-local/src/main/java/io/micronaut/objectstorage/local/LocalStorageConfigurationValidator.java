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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
        List<ResolvedConfiguration> resolved = enabled.stream().map(ResolvedConfiguration::of).toList();
        for (ResolvedConfiguration configuration : resolved) {
            for (ResolvedConfiguration other : resolved) {
                if (configuration.root().equals(other.root()) &&
                    !configuration.configuration().getInternalDirectory().equals(other.configuration().getInternalDirectory())) {
                    throw new ConfigurationException("Local storage configurations '" + configuration.configuration().getName() + "' and '" + other.configuration().getName()
                        + "' share a storage root but use different internal-directory values");
                }
            }
        }
        for (ResolvedConfiguration configuration : resolved) {
            for (ResolvedConfiguration other : resolved) {
                boolean reservedSiblingName = configuration.root().equals(other.root()) && other.bucket().getFileName() != null &&
                    configuration.configuration().getInternalDirectory().equalsIgnoreCase(other.bucket().getFileName().toString());
                if (reservedSiblingName || configuration.internal().startsWith(other.bucket()) || other.bucket().startsWith(configuration.internal())) {
                    throw new ConfigurationException("Local storage internal-directory for '" + configuration.configuration().getName()
                        + "' collides with configured bucket '" + other.configuration().getName() + "': " + other.bucket());
                }
            }
        }
        // Inspect custom layouts only after all configuration conflicts have been rejected.
        enabled.forEach(LocalStorageLayout::new);
    }

    /**
     * Resolves existing ancestors so aliases compare equally even before the storage root exists.
     * Missing suffixes are appended without creating any directories.
     */
    private static Path resolvePhysicalPath(Path path) {
        try {
            return path.toRealPath();
        } catch (NoSuchFileException e) {
            Path parent = path.getParent();
            if (parent == null || Files.isSymbolicLink(path)) {
                throw new ConfigurationException("Cannot resolve local storage path: " + path, e);
            }
            return resolvePhysicalPath(parent).resolve(path.getFileName());
        } catch (IOException e) {
            throw new ConfigurationException("Cannot resolve local storage path: " + path, e);
        }
    }

    private record ResolvedConfiguration(LocalStorageConfiguration configuration, Path bucket, Path root, Path internal) {
        private static ResolvedConfiguration of(LocalStorageConfiguration configuration) {
            Path bucket = configuration.getPath().toAbsolutePath().normalize();
            Path parent = bucket.getParent();
            Path root = resolvePhysicalPath(parent == null ? bucket : parent);
            return new ResolvedConfiguration(configuration, resolvePhysicalPath(bucket), root,
                resolvePhysicalPath(root.resolve(configuration.getInternalDirectory())));
        }
    }
}
