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
package io.micronaut.objectstorage.relational;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Native relational metadata for one stored object.
 *
 * @param key The exposed object key.
 * @param eTag The object ETag if known.
 * @param contentLength The object length if known.
 * @param contentType The object content type if known.
 * @param metadata The object metadata.
 * @param updatedAt The last update timestamp if known.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
public record RelationalStoredObject(
    @NonNull String key,
    @Nullable String eTag,
    @Nullable Long contentLength,
    @Nullable String contentType,
    @NonNull Map<String, String> metadata,
    @Nullable Instant updatedAt
) {

    public RelationalStoredObject(
        @NonNull String key,
        @Nullable String eTag,
        @Nullable Long contentLength,
        @Nullable String contentType,
        @NonNull Map<String, String> metadata,
        @Nullable Instant updatedAt
    ) {
        this.key = key;
        this.eTag = eTag;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.metadata = Map.copyOf(metadata);
        this.updatedAt = updatedAt;
    }

    /**
     * @return The object content type if known.
     */
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * @return The object content length if known.
     */
    @NonNull
    public Optional<Long> getContentLength() {
        return Optional.ofNullable(contentLength);
    }

    /**
     * @return The object update timestamp if known.
     */
    @NonNull
    public Optional<Instant> getUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }
}
