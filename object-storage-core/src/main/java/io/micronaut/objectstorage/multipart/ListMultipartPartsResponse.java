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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Paginated multipart part listing response.
 *
 * @param parts the ordered multipart parts in the current page
 * @param continuationToken the opaque continuation token for the next page
 * @since 3.1.0
 */
public record ListMultipartPartsResponse(@NonNull List<MultipartPart> parts,
                                         @Nullable String continuationToken) {

    /**
     * @param parts the ordered multipart parts in the current page
     */
    public ListMultipartPartsResponse(@NonNull List<MultipartPart> parts) {
        this(parts, null);
    }

    /**
     * @param parts the ordered multipart parts in the current page
     * @param continuationToken the opaque continuation token for the next page
     */
    public ListMultipartPartsResponse {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        continuationToken = normalize(continuationToken);
    }

    /**
     * @return the ordered multipart parts in the current page
     */
    @NonNull
    public List<MultipartPart> getParts() {
        return parts;
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
