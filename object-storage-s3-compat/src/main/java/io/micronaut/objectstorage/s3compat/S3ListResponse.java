/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Internal paginated listing response for the S3-compatible transport layer.
 *
 * @param objects the objects in the current page
 * @param continuationToken the opaque continuation token for the next page
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public record S3ListResponse(
    @NonNull List<S3ObjectSummary> objects,
    @Nullable String continuationToken
) {

    public S3ListResponse(@NonNull List<S3ObjectSummary> objects, @Nullable String continuationToken) {
        this.objects = List.copyOf(objects);
        this.continuationToken = continuationToken == null || continuationToken.isEmpty() ? null : continuationToken;
    }

    /**
     * @return the opaque continuation token for the next page if present
     */
    @NonNull
    public Optional<String> getContinuationToken() {
        return Optional.ofNullable(continuationToken);
    }
}
