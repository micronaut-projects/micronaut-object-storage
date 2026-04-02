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
package io.micronaut.objectstorage.azure;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Azure bucket operations.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@EachBean(BlobServiceClient.class)
public class AzureBlobBucketOperations
    implements BucketOperations<BlobParallelUploadOptions, BlockBlobItem, Response<Void>> {

    private final BlobServiceClient blobServiceClient;

    public AzureBlobBucketOperations(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    @Override
    public void createBucket(String name) {
        blobServiceClient.createBlobContainer(name);
    }

    @Override
    public void deleteBucket(String name) {
        blobServiceClient.deleteBlobContainer(name);
    }

    @Override
    public Set<String> listBuckets() {
        return blobServiceClient.listBlobContainers().stream()
            .map(BlobContainerItem::getName)
            .collect(Collectors.toSet());
    }

    @Override
    public ObjectStorageOperations<BlobParallelUploadOptions, BlockBlobItem, Response<Void>> storageForBucket(String bucket) {
        return new AzureBlobStorageOperations(blobServiceClient.getBlobContainerClient(bucket));
    }
}
