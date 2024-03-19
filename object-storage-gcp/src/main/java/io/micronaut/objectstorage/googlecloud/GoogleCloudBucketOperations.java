/*
 * Copyright 2017-2023 original authors
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
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * GCP bucket operations.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@Singleton
public class GoogleCloudBucketOperations
    implements BucketOperations<BlobInfo.Builder, Blob, Boolean> {

    private final InputStreamMapper inputStreamMapper;
    private final Storage storage;

    public GoogleCloudBucketOperations(InputStreamMapper inputStreamMapper, Storage storage) {
        this.inputStreamMapper = inputStreamMapper;
        this.storage = storage;
    }

    @Override
    public void createBucket(String name) {
        storage.create(BucketInfo.newBuilder(name).build());
    }

    @Override
    public void deleteBucket(String name) {
        storage.delete(name);
    }

    @Override
    public Set<String> listBuckets() {
        return storage.list().streamAll()
            .map(b -> b.asBucketInfo().getName())
            .collect(Collectors.toSet());
    }

    @Override
    public ObjectStorageOperations<BlobInfo.Builder, Blob, Boolean> storageForBucket(String bucket) {
        GoogleCloudStorageConfiguration configuration = new GoogleCloudStorageConfiguration("");
        configuration.setBucket(bucket);
        return new GoogleCloudStorageOperations(configuration, inputStreamMapper, storage);
    }
}
