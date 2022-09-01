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
package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageEntry;

import java.io.InputStream;

/**
 * An {@link ObjectStorageEntry} implementation for Oracle Cloud Storage.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public class OracleCloudStorageEntry implements ObjectStorageEntry {

    private final GetObjectResponse objectResponse;
    private final String key;

    OracleCloudStorageEntry(String key, GetObjectResponse objectResponse) {
        this.objectResponse = objectResponse;
        this.key = key;
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    @Override
    public InputStream getInputStream() {
        return objectResponse.getInputStream();
    }
}
