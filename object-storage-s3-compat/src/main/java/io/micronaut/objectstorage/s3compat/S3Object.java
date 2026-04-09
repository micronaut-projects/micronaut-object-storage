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
import io.micronaut.objectstorage.ObjectStorageEntry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * Internal object representation for the S3-compatible transport layer.
 *
 * @param key the exposed object key
 * @param entry the underlying object entry
 * @param size the object length if known
 * @param lastModified the object modification time if known
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public record S3Object(
    @NonNull String key,
    @NonNull ObjectStorageEntry<?> entry,
    @Nullable Long size,
    @Nullable Instant lastModified
) {

    /**
     * @return the object length if known
     */
    @NonNull
    public Optional<Long> getSize() {
        return Optional.ofNullable(size);
    }

    /**
     * @return the last modification time if known
     */
    @NonNull
    public Optional<Instant> getLastModified() {
        return Optional.ofNullable(lastModified);
    }
}
