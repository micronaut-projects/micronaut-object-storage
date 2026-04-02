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
package io.micronaut.objectstorage.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to create a pre-signed upload for a single object key.
 *
 * @since 3.1.0
 */
public final class CreatePresignedUploadRequest {

    private final String key;
    private final Duration expiresIn;
    @Nullable
    private String contentType;
    @Nullable
    private Long contentLength;
    private Map<String, String> metadata = Collections.emptyMap();

    /**
     * @param key the object key to be uploaded
     * @param expiresIn the signature validity duration
     */
    public CreatePresignedUploadRequest(@NonNull String key, @NonNull Duration expiresIn) {
        this.key = Objects.requireNonNull(key, "key");
        this.expiresIn = Objects.requireNonNull(expiresIn, "expiresIn");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        if (expiresIn.isZero() || expiresIn.isNegative()) {
            throw new IllegalArgumentException("expiresIn must be greater than 0");
        }
    }

    /**
     * @return the object key to be uploaded
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * @return the signature validity duration
     */
    @NonNull
    public Duration getExpiresIn() {
        return expiresIn;
    }

    /**
     * @return the content type required by the signed upload, if present
     */
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * @param contentType the content type required by the signed upload
     */
    public void setContentType(@NonNull String contentType) {
        this.contentType = Objects.requireNonNull(contentType, "contentType");
    }

    /**
     * @return the content length required by the signed upload, if present
     */
    @NonNull
    public Optional<Long> getContentLength() {
        return Optional.ofNullable(contentLength);
    }

    /**
     * @param contentLength the content length required by the signed upload
     */
    public void setContentLength(long contentLength) {
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be greater than 0");
        }
        this.contentLength = contentLength;
    }

    /**
     * @return metadata to include in the signed upload request
     */
    @NonNull
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * @param metadata metadata to include in the signed upload request
     */
    public void setMetadata(@NonNull Map<String, String> metadata) {
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
