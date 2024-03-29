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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Upload request implementation using byte array.
 *
 * @author Burt Beckwith
 * @since 1.0
 */
public class BytesUploadRequest extends AbstractUploadRequest implements UploadRequest {

    @NonNull
    private final byte[] bytes;

    @NonNull
    private final String key;

    public BytesUploadRequest(@NonNull byte[] bytes, @NonNull String key) {
        this(bytes, URLConnection.guessContentTypeFromName(key), key, Collections.emptyMap());
    }

    public BytesUploadRequest(@NonNull byte[] bytes,
                              @NonNull String key,
                              @NonNull String contentType) {
        this(bytes, contentType, key, Collections.emptyMap());
    }

    public BytesUploadRequest(@NonNull byte[] bytes,
                              @Nullable String contentType,
                              @NonNull String key,
                              @NonNull Map<String, String> metadata) {
        this.bytes = bytes;
        this.contentType = contentType;
        this.key = key;
        this.metadata = metadata;
    }

    @Override
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    @Override
    @NonNull
    public Optional<Long> getContentSize() {
        return Optional.of((long) bytes.length);
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    /**
     * @return the byte array source.
     */
    @NonNull
    public byte[] getBytes() {
        return bytes;
    }

}
