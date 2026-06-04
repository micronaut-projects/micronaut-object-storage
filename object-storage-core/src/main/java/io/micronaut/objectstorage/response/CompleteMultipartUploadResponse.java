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
 * Multipart upload completion response.
 *
 * @param <R> Cloud vendor-specific complete multipart upload response
 * @since 3.0.1
 */
@DefaultImplementation(DefaultCompleteMultipartUploadResponse.class)
public interface CompleteMultipartUploadResponse<R> {

    /**
     * Creates a multipart upload completion response.
     *
     * @param upload the multipart upload handle
     * @param eTag the final object entity tag
     * @param nativeResponse the native provider response
     * @param <R> Cloud vendor-specific complete multipart upload response
     * @return the response
     */
    @NonNull
    static <R> CompleteMultipartUploadResponse<R> of(@NonNull MultipartUploadHandle upload,
                                                     @NonNull String eTag,
                                                     @NonNull R nativeResponse) {
        return new DefaultCompleteMultipartUploadResponse<>(upload, eTag, nativeResponse);
    }

    /**
     * @return the multipart upload handle
     */
    @NonNull
    MultipartUploadHandle getUpload();

    /**
     * @return the final object entity tag
     */
    @NonNull
    String getETag();

    /**
     * @return the native provider response
     */
    @NonNull
    R getNativeResponse();
}
