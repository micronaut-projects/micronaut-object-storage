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

import io.micronaut.objectstorage.MultipartPart;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Paginated multipart part listing response.
 *
 * @since 3.0.1
 */
public final class ListMultipartPartsResponse {

    private final List<MultipartPart> parts;
    @Nullable
    private final String continuationToken;

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
    public ListMultipartPartsResponse(@NonNull List<MultipartPart> parts, @Nullable String continuationToken) {
        this.parts = List.copyOf(parts);
        this.continuationToken = continuationToken == null || continuationToken.isEmpty() ? null : continuationToken;
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
}
