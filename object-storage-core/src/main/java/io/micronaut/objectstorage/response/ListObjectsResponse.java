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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Paginated object listing response.
 *
 * @since 3.0.0
 */
public final class ListObjectsResponse {

    private final List<String> keys;
    @Nullable
    private final String continuationToken;

    /**
     * @param keys the ordered keys in the current page
     */
    public ListObjectsResponse(@NonNull List<String> keys) {
        this(keys, null);
    }

    /**
     * @param keys the ordered keys in the current page
     * @param continuationToken the opaque continuation token for the next page
     */
    public ListObjectsResponse(@NonNull List<String> keys, @Nullable String continuationToken) {
        this.keys = List.copyOf(keys);
        this.continuationToken = continuationToken == null || continuationToken.isEmpty() ? null : continuationToken;
    }

    /**
     * @return the ordered keys in the current page
     */
    @NonNull
    public List<String> getKeys() {
        return keys;
    }

    /**
     * @return the opaque continuation token for the next page, if present
     */
    @NonNull
    public Optional<String> getContinuationToken() {
        return Optional.ofNullable(continuationToken);
    }
}
