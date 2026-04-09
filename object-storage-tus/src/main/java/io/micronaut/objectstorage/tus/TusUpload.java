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
package io.micronaut.objectstorage.tus;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Persistent tus upload state.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 * @param id The upload identifier
 * @param key The destination object key
 * @param uploadLength The declared upload length
 * @param offset The committed upload offset
 * @param contentType The completed object content type
 * @param metadata User metadata to persist with the completed object
 * @param status The current upload status
 */
public record TusUpload(
    @NonNull String id,
    @NonNull String key,
    long uploadLength,
    long offset,
    @Nullable String contentType,
    @NonNull Map<String, String> metadata,
    @NonNull TusUploadStatus status
) {
    public boolean inProgress() {
        return status == TusUploadStatus.IN_PROGRESS;
    }

    public boolean completed() {
        return status == TusUploadStatus.COMPLETED;
    }

    public boolean aborted() {
        return status == TusUploadStatus.ABORTED;
    }

    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }
}
