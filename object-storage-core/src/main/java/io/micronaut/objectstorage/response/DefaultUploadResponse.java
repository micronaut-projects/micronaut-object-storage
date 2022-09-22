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

import io.micronaut.core.annotation.NonNull;

/**
 * Default implementation of {@link UploadResponse}.
 *
 * @param <R> Cloud vendor-specific upload response
 */
public class DefaultUploadResponse<R> implements UploadResponse<R> {

    private final String key;
    private final String eTag;
    private final R nativeResponse;

    protected DefaultUploadResponse(String key, String eTag, R nativeResponse) {
        this.key = key;
        this.eTag = eTag;
        this.nativeResponse = nativeResponse;
    }

    @NonNull
    @Override
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public String getETag() {
        return eTag;
    }

    @NonNull
    @Override
    public R getNativeResponse() {
        return nativeResponse;
    }
}
