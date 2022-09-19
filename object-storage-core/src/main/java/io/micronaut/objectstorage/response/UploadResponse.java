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

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.NonNull;

/**
 * Object storage upload response.
 *
 * @param <R> Cloud vendor-specific upload response
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@DefaultImplementation(DefaultUploadResponse.class)
public interface UploadResponse<R> {

    /**
     * Creates an instance from the given parameters.
     *
     * @param key the key under which the file will be stored.
     * @param eTag the entity tag of the object stored.
     * @param nativeResponse upload response object.
     * @param <R> Cloud vendor-specific upload response
     *
     * @return an upload response instance.
     */
    @NonNull
    static <R> UploadResponse<R> of(@NonNull String key, @NonNull String eTag, @NonNull R nativeResponse) {
        return new DefaultUploadResponse<>(key, eTag, nativeResponse);
    }

    /**
     * @return The key under which the object was stored.
     */
    @NonNull
    String getKey();

    /**
     * @return the entity tag of the object stored (an identifier for a specific version of the object).
     */
    @NonNull
    String getETag();

    /**
     * @return Cloud vendor-specific upload response
     */
    @NonNull
    R getNativeResponse();

}
