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

import java.util.Objects;

/**
 * Opaque multipart upload identity.
 *
 * @param key the target object key
 * @param uploadId the opaque provider upload id
 * @since 3.1.0
 */
public record MultipartUploadHandle(@NonNull String key,
                                    @NonNull String uploadId) {

    /**
     * Compact constructor.
     */
    public MultipartUploadHandle {
        key = Objects.requireNonNull(key, "key");
        uploadId = Objects.requireNonNull(uploadId, "uploadId");
    }

    /**
     * @return the target object key
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * @return the opaque provider upload id
     */
    @NonNull
    public String getUploadId() {
        return uploadId;
    }
}
