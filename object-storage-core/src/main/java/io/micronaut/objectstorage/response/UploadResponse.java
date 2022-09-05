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
     * @return the entity tag of the object stored (an identifier for a specific version of the object).
     */
    String getETag();

    /**
     * @return Cloud vendor-specific upload response
     */
    R getNativeResponse();

    static <R> UploadResponse<R> of(String eTag, R nativeResponse) {
        return new DefaultUploadResponse<>(eTag, nativeResponse);
    }

}
