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

import io.micronaut.core.naming.Named;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Backend contract for resumable tus uploads.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
public interface TusUploadBackend extends Named {

    @NonNull
    TusUpload create(@NonNull String key,
                     long uploadLength,
                     String contentType,
                     @NonNull Map<String, String> metadata);

    @NonNull
    Optional<TusUpload> find(@NonNull String uploadId);

    @NonNull
    TusUpload append(@NonNull String uploadId, long expectedOffset, byte[] chunk);

    void abort(@NonNull String uploadId);
}
