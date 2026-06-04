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
import io.micronaut.objectstorage.MultipartUploadHandle;
import org.jspecify.annotations.NonNull;

/**
 * Multipart upload creation response.
 *
 * @param <R> Cloud vendor-specific create multipart upload response
 * @since 3.0.1
 */
@DefaultImplementation(DefaultCreateMultipartUploadResponse.class)
public interface CreateMultipartUploadResponse<R> {

    /**
     * Creates a multipart upload creation response.
     *
     * @param upload the multipart upload handle
     * @param nativeResponse the native provider response
     * @param <R> Cloud vendor-specific create multipart upload response
     * @return the response
     */
    @NonNull
    static <R> CreateMultipartUploadResponse<R> of(@NonNull MultipartUploadHandle upload, @NonNull R nativeResponse) {
        return new DefaultCreateMultipartUploadResponse<>(upload, nativeResponse);
    }

    /**
     * @return the multipart upload handle
     */
    @NonNull
    MultipartUploadHandle getUpload();

    /**
     * @return the native provider response
     */
    @NonNull
    R getNativeResponse();
}
