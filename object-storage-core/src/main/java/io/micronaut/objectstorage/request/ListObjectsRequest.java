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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Paginated object listing request.
 *
 * @since 3.0.0
 */
public record ListObjectsRequest(int pageSize,
                                 @Nullable String prefix,
                                 @Nullable String continuationToken) {

    /**
     * @param pageSize the maximum number of keys to return
     */
    public ListObjectsRequest(int pageSize) {
        this(pageSize, null, null);
    }

    /**
     * @param pageSize the maximum number of keys to return
     * @param prefix the prefix to filter keys by
     */
    public ListObjectsRequest(int pageSize, @Nullable String prefix) {
        this(pageSize, prefix, null);
    }

    public ListObjectsRequest {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        prefix = normalize(prefix);
        continuationToken = normalize(continuationToken);
    }

    /**
     * @return the maximum number of keys to return
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @return the prefix to filter keys by, if present
     */
    @NonNull
    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
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
