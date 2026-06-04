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
package io.micronaut.objectstorage.response;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.objectstorage.MultipartPart;
import org.jspecify.annotations.NonNull;

/**
 * Multipart part upload response.
 *
 * @param <R> Cloud vendor-specific upload part response
 * @since 3.0.1
 */
@DefaultImplementation(DefaultUploadPartResponse.class)
public interface UploadPartResponse<R> {

    /**
     * Creates a multipart upload part response.
     *
     * @param part the uploaded part metadata
     * @param nativeResponse the native provider response
     * @param <R> Cloud vendor-specific upload part response
     * @return the response
     */
    @NonNull
    static <R> UploadPartResponse<R> of(@NonNull MultipartPart part, @NonNull R nativeResponse) {
        return new DefaultUploadPartResponse<>(part, nativeResponse);
    }

    /**
     * @return the uploaded part metadata
     */
    @NonNull
    MultipartPart getPart();

    /**
     * @return the native provider response
     */
    @NonNull
    R getNativeResponse();
}
