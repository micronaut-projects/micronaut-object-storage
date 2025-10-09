/*
 * Copyright 2017-2024 original authors
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.StreamingFileUpload;

/**
 * 
 * An {@link UploadRequest} backed by a {@link StreamingFileUpload}.
 * @since 2.7.0
 */
public class StreamingFileUploadRequest implements UploadRequest {

    @NonNull
    private final StreamingFileUpload streamingFileUpload;
    @NonNull
    private final String key;

    @NonNull
    private Map<String, String> metadata;

    public StreamingFileUploadRequest(@NonNull StreamingFileUpload streamingFileUpload) {
        this(streamingFileUpload, streamingFileUpload.getName(), Collections.emptyMap());
    }

    public StreamingFileUploadRequest(@NonNull StreamingFileUpload streamingFileUpload, @NonNull String key) {
        this(streamingFileUpload, key, Collections.emptyMap());
    }

    public StreamingFileUploadRequest(@NonNull StreamingFileUpload streamingFileUpload, @NonNull String key, @NonNull Map<String, String> metadata) {
        this.streamingFileUpload = streamingFileUpload;
        this.key = key;
        this.metadata = metadata;
    }

    @NonNull
    @Override
    public Optional<String> getContentType() {
        return streamingFileUpload.getContentType()
            .map(MediaType::getName);
    }

    @NonNull
    @Override
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public Optional<Long> getContentSize() {
        return Optional.of(streamingFileUpload.getSize());
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
    	return streamingFileUpload.asInputStream();
    }

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public void setMetadata(@NonNull Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
