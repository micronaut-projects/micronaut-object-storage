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
package io.micronaut.objectstorage.metadata;

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Map;

/**
 * Portable bucket or container metadata write model.
 *
 * <p>This write model represents a full metadata snapshot. Implementations should treat save
 * operations as replace/upsert, not patch/merge.</p>
 *
 * @param name The logical bucket or container name.
 * @param metadata User metadata.
 * @param attributes Portable custom attributes.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
public record BucketMetadataWrite(
    @NonNull String name,
    @NonNull Map<String, String> metadata,
    @NonNull Map<String, String> attributes
) {
    public BucketMetadataWrite {
        metadata = Map.copyOf(metadata);
        attributes = Map.copyOf(attributes);
    }

    /**
     * Creates a metadata write with metadata only.
     *
     * @param name The logical bucket or container name.
     * @param metadata User metadata.
     */
    public BucketMetadataWrite(@NonNull String name, @NonNull Map<String, String> metadata) {
        this(name, metadata, Collections.emptyMap());
    }
}
