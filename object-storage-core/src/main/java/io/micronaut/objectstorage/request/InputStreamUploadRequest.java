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
package io.micronaut.objectstorage.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link UploadRequest} backed by an {@link InputStream}.
 *
 * <p>The request returns the same stream instance and does not copy or buffer its contents. Callers
 * must not access the stream while an upload is in progress. Provider implementations may close the
 * stream, so callers that own it should ensure it is closed after a blocking upload returns or after
 * a reactive upload terminates.</p>
 *
 * <p>Because the stream is not recreated, automatic retries may require a stream that supports
 * {@link InputStream#mark(int)} and {@link InputStream#reset()}. A non-repeatable stream may fail if a
 * provider retries after consuming part of it. Providing the content length allows providers to
 * stream the request without buffering and is required by some provider operations.</p>
 *
 * @since 3.1.0
 */
public class InputStreamUploadRequest extends AbstractUploadRequest {

    @NonNull
    private final InputStream inputStream;

    @NonNull
    private final String key;

    @Nullable
    private final Long contentLength;

    /**
     * Creates a request with an unknown content length and a content type inferred from the key.
     *
     * @param inputStream the source input stream.
     * @param key the key under which the object will be stored.
     * @since 3.1.0
     */
    public InputStreamUploadRequest(@NonNull InputStream inputStream, @NonNull String key) {
        this(inputStream, key, URLConnection.guessContentTypeFromName(key), null);
    }

    /**
     * Creates a request with a known content length and a content type inferred from the key.
     *
     * @param inputStream the source input stream.
     * @param key the key under which the object will be stored.
     * @param contentLength the number of bytes to read from the stream.
     * @since 3.1.0
     */
    public InputStreamUploadRequest(@NonNull InputStream inputStream,
                                    @NonNull String key,
                                    long contentLength) {
        this(inputStream, key, URLConnection.guessContentTypeFromName(key), contentLength);
    }

    /**
     * Creates a request with optional content type and content length.
     *
     * @param inputStream the source input stream.
     * @param key the key under which the object will be stored.
     * @param contentType the content type, or {@code null} when unknown.
     * @param contentLength the number of bytes to read from the stream, or {@code null} when unknown.
     * @since 3.1.0
     */
    public InputStreamUploadRequest(@NonNull InputStream inputStream,
                                    @NonNull String key,
                                    @Nullable String contentType,
                                    @Nullable Long contentLength) {
        this(inputStream, key, contentType, contentLength, Collections.emptyMap());
    }

    /**
     * Creates a request with optional content type, optional content length, and metadata.
     *
     * @param inputStream the source input stream.
     * @param key the key under which the object will be stored.
     * @param contentType the content type, or {@code null} when unknown.
     * @param contentLength the number of bytes to read from the stream, or {@code null} when unknown.
     * @param metadata key-value pairs to store with the object.
     * @since 3.1.0
     */
    public InputStreamUploadRequest(@NonNull InputStream inputStream,
                                    @NonNull String key,
                                    @Nullable String contentType,
                                    @Nullable Long contentLength,
                                    @NonNull Map<String, String> metadata) {
        if (contentLength != null && contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        this.inputStream = inputStream;
        this.key = key;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.metadata = metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Optional<Long> getContentSize() {
        return Optional.ofNullable(contentLength);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public InputStream getInputStream() {
        return inputStream;
    }
}
