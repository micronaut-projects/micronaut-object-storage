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
 * Multipart part listing response.
 *
 * @param parts the uploaded parts in the current page
 * @param nextPartNumberMarker the next page cursor when truncated
 * @param truncated whether more parts are available
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public record S3MultipartListPartsResponse(
    @NonNull List<S3MultipartPart> parts,
    @Nullable Integer nextPartNumberMarker,
    boolean truncated
) {

    public S3MultipartListPartsResponse(@NonNull List<S3MultipartPart> parts,
                                        @Nullable Integer nextPartNumberMarker,
                                        boolean truncated) {
        this.parts = List.copyOf(parts);
        this.nextPartNumberMarker = nextPartNumberMarker;
        this.truncated = truncated;
    }

    /**
     * @return the next page cursor if the response is truncated
     */
    @NonNull
    public Optional<Integer> getNextPartNumberMarker() {
        return Optional.ofNullable(nextPartNumberMarker);
    }
}
