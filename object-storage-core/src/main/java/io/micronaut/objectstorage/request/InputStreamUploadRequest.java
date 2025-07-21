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

package io.micronaut.objectstorage.request;

import java.io.InputStream;
import java.util.Optional;

/**
 *
 * An {@link UploadRequest} backed by a {@link InputStream}.
 * @author Munish Chouhan
 * @since 2.9.1
 */
public class InputStreamUploadRequest implements UploadRequest {

    private final InputStream inputStream;
    private final String key;
    private final String contentType;
    private final Long contentLength;

    public InputStreamUploadRequest(InputStream inputStream, String key, String contentType, Long contentLength) {
        this.inputStream = inputStream;
        this.key = key;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Optional<Long> getContentSize() {
        return Optional.ofNullable(contentLength);
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }
}
