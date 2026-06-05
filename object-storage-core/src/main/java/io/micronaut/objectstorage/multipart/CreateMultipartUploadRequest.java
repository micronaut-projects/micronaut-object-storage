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
package io.micronaut.objectstorage.multipart;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Multipart upload creation request.
 *
 * @param key the target object key
 * @param contentType the optional content type
 * @param metadata the optional object metadata
 * @since 3.1.0
 */
public record CreateMultipartUploadRequest(@NonNull String key,
                                           @Nullable String contentType,
                                           @NonNull Map<String, String> metadata) {

    /**
     * @param key the target object key
     */
    public CreateMultipartUploadRequest(@NonNull String key) {
        this(key, null, Map.of());
    }

    /**
     * @param key the target object key
     * @param contentType the optional content type
     */
    public CreateMultipartUploadRequest(@NonNull String key, @Nullable String contentType) {
        this(key, contentType, Map.of());
    }

    /**
     * @param key the target object key
     * @param contentType the optional content type
     * @param metadata the optional object metadata
     */
    public CreateMultipartUploadRequest {
        key = Objects.requireNonNull(key, "key");
        contentType = normalize(contentType);
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * @return the target object key
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * @return the optional content type
     */
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * @return the optional object metadata
     */
    @NonNull
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
