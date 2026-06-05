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

import java.util.Objects;

/**
 * Default implementation of {@link UploadPartResponse}.
 *
 * @param <R> Cloud vendor-specific upload part response
 * @param part the uploaded part metadata
 * @param nativeResponse the native provider response
 * @since 3.1.0
 */
public record DefaultUploadPartResponse<R>(@NonNull MultipartPart part,
                                           @NonNull R nativeResponse) implements UploadPartResponse<R> {

    /**
     * Compact constructor.
     */
    public DefaultUploadPartResponse {
        part = Objects.requireNonNull(part, "part");
        nativeResponse = Objects.requireNonNull(nativeResponse, "nativeResponse");
    }

    @Override
    @NonNull
    public MultipartPart getPart() {
        return part;
    }

    @Override
    @NonNull
    public R getNativeResponse() {
        return nativeResponse;
    }
}
