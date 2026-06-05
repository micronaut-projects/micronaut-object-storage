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
 * Default implementation of {@link CompleteMultipartUploadResponse}.
 *
 * @param <R> Cloud vendor-specific complete multipart upload response
 * @param upload the multipart upload handle
 * @param eTag the final object entity tag
 * @param nativeResponse the native provider response
 * @since 3.1.0
 */
public record DefaultCompleteMultipartUploadResponse<R>(@NonNull MultipartUploadHandle upload,
                                                        @NonNull String eTag,
                                                        @NonNull R nativeResponse) implements CompleteMultipartUploadResponse<R> {

    /**
     * Compact constructor.
     */
    public DefaultCompleteMultipartUploadResponse {
        upload = Objects.requireNonNull(upload, "upload");
        eTag = Objects.requireNonNull(eTag, "eTag");
        nativeResponse = Objects.requireNonNull(nativeResponse, "nativeResponse");
    }

    @Override
    @NonNull
    public MultipartUploadHandle getUpload() {
        return upload;
    }

    @Override
    @NonNull
    public String getETag() {
        return eTag;
    }

    @Override
    @NonNull
    public R getNativeResponse() {
        return nativeResponse;
    }
}
