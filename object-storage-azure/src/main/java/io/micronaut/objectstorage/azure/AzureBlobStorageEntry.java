/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.objectstorage.azure;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.models.BlobProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.objectstorage.ObjectStorageEntry;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link ObjectStorageEntry} implementation for Azure Blob Storage.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public class AzureBlobStorageEntry implements ObjectStorageEntry<BinaryData> {

    @NonNull
    private final String key;

    @NonNull
    private final BinaryData data;

    @Nullable
    private BlobProperties blobProperties;

    /**
     * @param key the key
     * @param data the binary data
     * @deprecated Use {@link #AzureBlobStorageEntry(String, BinaryData, BlobProperties)}
     */
    @Deprecated
    public AzureBlobStorageEntry(@NonNull String key, @NonNull BinaryData data) {
        this.key = key;
        this.data = data;
    }

    public AzureBlobStorageEntry(@NonNull String key, @NonNull BinaryData data,
                                 @Nullable BlobProperties blobProperties) {
        this.key = key;
        this.data = data;
        this.blobProperties = blobProperties;
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        return data.toStream();
    }

    @NonNull
    @Override
    public BinaryData getNativeEntry() {
        return data;
    }

    /**
     * @return The {@link BlobProperties}.
     * @since 1.1.0
     */
    public BlobProperties getBlobProperties() {
        return this.blobProperties;
    }

    @NonNull
    @Override
    public Map<String, String> getMetadata() {
        return Optional.ofNullable(blobProperties)
            .map(BlobProperties::getMetadata)
            .orElse(Collections.emptyMap());
    }
}
