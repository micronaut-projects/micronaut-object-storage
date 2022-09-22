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
package io.micronaut.objectstorage.aws;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageEntry;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link ObjectStorageEntry} implementation for AWS S3.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public class AwsS3ObjectStorageEntry implements ObjectStorageEntry<GetObjectResponse> {

    @NonNull
    private final String key;

    @NonNull
    private final ResponseInputStream<GetObjectResponse> responseInputStream;

    public AwsS3ObjectStorageEntry(@NonNull String key,
                                   @NonNull ResponseInputStream<GetObjectResponse> responseInputStream) {
        this.responseInputStream = responseInputStream;
        this.key = key;
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        return responseInputStream;
    }

    @NonNull
    @Override
    public GetObjectResponse getNativeEntry() {
        return responseInputStream.response();
    }

    @NonNull
    @Override
    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(responseInputStream.response().metadata());
    }

    @NonNull
    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(responseInputStream.response().contentType());
    }
}
