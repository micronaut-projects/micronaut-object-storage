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

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerProperties;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Azure bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(BlobServiceClient.class)
public class AzureBlobBucketOperations implements BucketOperations<BlobContainerProperties> {

    private final BlobServiceClient blobServiceClient;

    public AzureBlobBucketOperations(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    @Override
    public void create(@NonNull String name) {
        blobServiceClient.createBlobContainer(name);
    }

    @Override
    public Optional<BucketEntry<BlobContainerProperties>> retrieve(@NonNull String name) {
        var containerClient = blobServiceClient.getBlobContainerClient(name);
        if (!containerClient.exists()) {
            return Optional.empty();
        }
        return Optional.of(new BucketEntry<>(name, containerClient.getProperties()));
    }

    @Override
    public void delete(@NonNull String name) {
        blobServiceClient.deleteBlobContainer(name);
    }
}
