/*
 * Copyright 2021 original authors
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
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.UploadRequest;

import java.io.InputStream;

/**
 * @author Pavol Gressa
 * @since 2.5
 */
public class OracleCloudObjectStorageEntry implements ObjectStorageEntry {

    private GetObjectResponse objectResponse;
    private final String key;

    OracleCloudObjectStorageEntry(String key, GetObjectResponse objectResponse) {
        this.objectResponse = objectResponse;
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public InputStream getInputStream() {
        return objectResponse.getInputStream();
    }
}
