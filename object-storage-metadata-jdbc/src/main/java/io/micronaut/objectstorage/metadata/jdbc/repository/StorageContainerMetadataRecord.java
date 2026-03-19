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
import io.micronaut.objectstorage.metadata.StorageContainerMetadata;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;

/**
 * Container metadata row plus resolved attribute map.
 *
 * @param metadata Persisted container metadata values
 * @param attributes Persisted container metadata attributes
 */
@Internal
public record StorageContainerMetadataRecord(
    @NonNull StorageContainerMetadata metadata,
    @NonNull Map<String, String> attributes
) {
    public StorageContainerMetadataRecord {
        Objects.requireNonNull(metadata, "metadata");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }
}
