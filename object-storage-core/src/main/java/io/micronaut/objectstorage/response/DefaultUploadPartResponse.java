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

import io.micronaut.objectstorage.MultipartPart;
import org.jspecify.annotations.NonNull;

/**
 * Default implementation of {@link UploadPartResponse}.
 *
 * @param <R> Cloud vendor-specific upload part response
 * @since 3.0.1
 */
public class DefaultUploadPartResponse<R> implements UploadPartResponse<R> {

    private final MultipartPart part;
    private final R nativeResponse;

    protected DefaultUploadPartResponse(MultipartPart part, R nativeResponse) {
        this.part = part;
        this.nativeResponse = nativeResponse;
    }

    @Override
    @NonNull
    public MultipartPart getPart() {
        return part;
    }

    @Override
    @NonNull
    public R getNativeResponse() {
        return nativeResponse;
    }
}
