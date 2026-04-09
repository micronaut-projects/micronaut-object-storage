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

import io.micronaut.objectstorage.MultipartUploadHandle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Multipart part listing request.
 *
 * @since 3.0.0
 */
public final class ListMultipartPartsRequest {

    private final MultipartUploadHandle upload;
    private final int pageSize;
    @Nullable
    private final String continuationToken;

    /**
     * @param upload the multipart upload handle
     * @param pageSize the maximum number of parts to return
     */
    public ListMultipartPartsRequest(@NonNull MultipartUploadHandle upload, int pageSize) {
        this(upload, pageSize, null);
    }

    /**
     * @param upload the multipart upload handle
     * @param pageSize the maximum number of parts to return
     * @param continuationToken the opaque continuation token for the next page
     */
    public ListMultipartPartsRequest(@NonNull MultipartUploadHandle upload, int pageSize, @Nullable String continuationToken) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.upload = Objects.requireNonNull(upload, "upload");
        this.pageSize = pageSize;
        this.continuationToken = normalize(continuationToken);
    }

    /**
     * @return the multipart upload handle
     */
    @NonNull
    public MultipartUploadHandle getUpload() {
        return upload;
    }

    /**
     * @return the maximum number of parts to return
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @return the opaque continuation token for the next page, if present
     */
    @NonNull
    public Optional<String> getContinuationToken() {
        return Optional.ofNullable(continuationToken);
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
