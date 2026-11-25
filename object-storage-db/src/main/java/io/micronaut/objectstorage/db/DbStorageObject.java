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
package io.micronaut.objectstorage.db;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A stored object row, exposed as the native type of the database storage.
 *
 * @param bucket The bucket.
 * @param key The object key.
 * @param contentLength The content length, if known.
 * @param contentType The content type, if known.
 * @param etag The entity tag (MD5 of the content in hexadecimal), if known.
 * @param lastModified The last modification time, if known.
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
public record DbStorageObject(
    @NonNull String bucket,
    @NonNull String key,
    @Nullable Long contentLength,
    @Nullable String contentType,
    @Nullable String etag,
    @Nullable Instant lastModified
) {
}
