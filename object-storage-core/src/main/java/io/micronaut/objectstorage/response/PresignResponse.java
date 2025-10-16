/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * Response returned after generating a pre-authorized (signed) request.
 *
 * @param url        The fully qualified URL that clients can use to perform the operation.
 * @param expiration The instant when the URL expires.
 * @param id         The PAR id (if any).
 *
 * @since 2.10.0
 */
public record PresignResponse(@NonNull URI url, @NonNull Instant expiration, @Nullable String id) {

    public PresignResponse(final URI url, final Instant expiration) {
        this(url, expiration, null);
    }

    public PresignResponse {
        requireNonNull(url, "url must not be null");
        requireNonNull(expiration, "expiration must not be null");
    }
}
