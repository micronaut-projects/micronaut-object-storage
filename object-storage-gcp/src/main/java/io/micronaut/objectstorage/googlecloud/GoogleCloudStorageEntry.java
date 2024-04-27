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
package io.micronaut.objectstorage.googlecloud;

import com.google.cloud.storage.Blob;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageEntry;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link ObjectStorageEntry} implementation for Google Cloud Storage.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public class GoogleCloudStorageEntry implements ObjectStorageEntry<Blob> {

    private final Blob blob;

    public GoogleCloudStorageEntry(Blob blob) {
        this.blob = blob;
    }

    @Override
    @NonNull
    public String getKey() {
        return blob.getName();
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        return Channels.newInputStream(blob.reader());
    }

    @NonNull
    @Override
    public Blob getNativeEntry() {
        return blob;
    }

    @NonNull
    @Override
    public Map<String, String> getMetadata() {
        return blob.getMetadata();
    }

    @NonNull
    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(blob.getContentType());
    }
}
