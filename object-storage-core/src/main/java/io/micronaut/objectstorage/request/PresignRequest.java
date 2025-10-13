/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Request object for generating pre-authorized (signed) operations against the object storage.
 *
 * @since 2.10.0
 */
public final class PresignRequest {
    /**
     * Supported operations that can be pre-authorized.
     */
    public enum Operation {
        /**
         * Generate a pre-signed request that allows downloading the object (typically HTTP GET).
         */
        DOWNLOAD,
        /**
         * Generate a pre-signed request that allows uploading the object (typically HTTP PUT or POST).
         */
        UPLOAD
    }

    private final String key;
    private final Operation operation;
    @Nullable
    private final Duration expiresIn;
    @Nullable
    private final Long contentLength;
    @Nullable
    private final String contentType;
    @Nullable
    private final String name;

    private PresignRequest(Builder builder) {
        this.key = builder.key;
        this.operation = builder.operation;
        this.expiresIn = builder.expiresIn;
        this.contentLength = builder.contentLength;
        this.contentType = builder.contentType;
        this.name = builder.name;
    }

    /**
     * @return The object key/path. (<code>/foo/bar/file</code>).
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * Operation for which the pre-signed request should be generated.
     *
     * @return The operation to pre-sign.
     */
    @NonNull
    public Operation getOperation() {
        return operation;
    }

    /**
     * Optional expiration override for the generated request. If empty, the implementation
     * will fall back to the configured default (typically 1 hour).
     *
     * @return An Optional expiration override for the generated request.
     */
    @NonNull
    public Optional<Duration> getExpiresIn() {
        return Optional.ofNullable(expiresIn);
    }

    /**
     * Optional expected content length (bytes) for upload operations. Implementations may
     * embed this as a maximum allowed size within the signature.
     *
     * @return An Optional expected content length for upload operations.
     */
    @NonNull
    public Optional<Long> getContentLength() {
        return Optional.ofNullable(contentLength);
    }

    /**
     * Optional content-type hint for upload operations.
     *
     * @return An Optional content-type hint for upload operations.
     */
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * Optional custom name for the pre-authorized request.
     * If empty, the provider will generate a default name.
     * @since 2.10
     *
     * @return An Optional containing the custom name for the pre-authorized request.
     */
    @NonNull
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * Creates a new builder.
     *
     * @param key       The object key/path.
     * @param operation The operation to pre-sign.
     * @return A new {@link Builder} instance.
     */
    @NonNull
    public static Builder builder(@NonNull String key, @NonNull Operation operation) {
        return new Builder(key, operation);
    }

    /**
     * Builder for {@link PresignRequest}.
     */
    public static final class Builder {
        private final String key;
        private final Operation operation;
        private Duration expiresIn;
        private Long contentLength;
        private String contentType;
        private String name;

        private Builder(String key, Operation operation) {
            this.key = Objects.requireNonNull(key, "key must not be null");
            this.operation = Objects.requireNonNull(operation, "operation must not be null");
        }

        /**
         * Sets a custom expiration duration for the generated signed request.
         *
         * @param expiresIn The expiration duration.
         * @return This builder.
         */
        @NonNull
        public Builder expiresIn(@NonNull Duration expiresIn) {
            this.expiresIn = Objects.requireNonNull(expiresIn, "expiresIn must not be null");
            return this;
        }

        /**
         * Sets the expected content length for upload operations.
         *
         * @param contentLength Measured in bytes.
         * @return This builder.
         */
        @NonNull
        public Builder contentLength(long contentLength) {
            if (contentLength <= 0) {
                throw new IllegalArgumentException("contentLength must be positive");
            }
            this.contentLength = contentLength;
            return this;
        }

        /**
         * Sets the content type hint for upload operations.
         *
         * @param contentType Typically a MIME type.
         * @return This builder.
         */
        @NonNull
        public Builder contentType(@NonNull String contentType) {
            this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
            return this;
        }

        /**
         * Sets a custom name for the generated pre-authorized request (when supported by the provider).
         *
         * @param name The desired name.
         * @return This builder.
         * @since 2.10
         */
        @NonNull
        public Builder name(@NonNull String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            return this;
        }

        /**
         * @return A new {@link PresignRequest}.
         */
        @NonNull
        public PresignRequest build() {
            return new PresignRequest(this);
        }
    }
}
