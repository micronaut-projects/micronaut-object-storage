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
package io.micronaut.objectstorage.aws;

import io.micronaut.objectstorage.ObjectStorageEntry;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;

/**
 * @author Pavol Gressa
 */
public class AwsS3ObjectStorageEntry implements ObjectStorageEntry {

    private final ResponseInputStream<GetObjectResponse> responseResponseInputStream;
    private final String key;

    AwsS3ObjectStorageEntry(String key, ResponseInputStream<GetObjectResponse> responseResponseInputStream) {
        this.responseResponseInputStream = responseResponseInputStream;
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public InputStream getInputStream() {
        return responseResponseInputStream;
    }
}
