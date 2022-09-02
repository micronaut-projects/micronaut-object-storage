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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageEntry;

import java.io.InputStream;

/**
 * An {@link ObjectStorageEntry} implementation for Azure Blob Storage.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public class AzureBlobStorageEntry implements ObjectStorageEntry {

    private final String key;

    private final BinaryData data;

    public AzureBlobStorageEntry(String key, BinaryData data) {
        this.key = key;
        this.data = data;
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    @Override
    public InputStream getInputStream() {
        return data.toStream();
    }
}
