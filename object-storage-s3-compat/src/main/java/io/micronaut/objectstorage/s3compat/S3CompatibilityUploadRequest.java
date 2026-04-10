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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.objectstorage.request.AbstractUploadRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

/**
 * Internal streaming upload request used by the S3 compatibility transport.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
final class S3CompatibilityUploadRequest extends AbstractUploadRequest {

    private final InputStream inputStream;
    private final String key;
    private final Long contentSize;

    S3CompatibilityUploadRequest(@NonNull InputStream inputStream,
                                 @NonNull String key,
                                 @Nullable Long contentSize,
                                 @Nullable String contentType) {
        this.inputStream = inputStream;
        this.key = key;
        this.contentSize = contentSize;
        this.contentType = contentType;
        this.metadata = Collections.emptyMap();
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
        return Optional.ofNullable(contentSize);
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return inputStream;
    }
}
