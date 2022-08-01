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

import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import com.azure.storage.common.implementation.Constants;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.*;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * @author Pavol Gressa
 */
@EachBean(BlobContainerClient.class)
@Singleton
public class AzureBlobContainer implements ObjectStorage {

    private final AzureBlobContainerConfiguration configuration;
    private final BlobContainerClient blobContainerClient;

    public AzureBlobContainer(@Parameter String name, BlobContainerClient blobContainerClient, BeanContext beanContext) {
        this.blobContainerClient = blobContainerClient;
        this.configuration = beanContext.getBean(AzureBlobContainerConfiguration.class, Qualifiers.byName(name));
    }

    @Override
    public UploadResponse put(UploadRequest uploadRequest) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(uploadRequest.getKey());

        Response<BlockBlobItem> blockBlobItemResponse = null;
        if (uploadRequest instanceof UploadRequest.FileUploadRequest) {
            UploadRequest.FileUploadRequest fileUploadRequest = (UploadRequest.FileUploadRequest) uploadRequest;

            BlobRequestConditions requestConditions = new BlobRequestConditions().setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);
            BlobUploadFromFileOptions options = new BlobUploadFromFileOptions(fileUploadRequest.getAbsolutePath())
                .setRequestConditions(requestConditions);
            blockBlobItemResponse = blobClient.uploadFromFileWithResponse(options, null, null);
        } else {
            BinaryData data = BinaryData.fromStream(uploadRequest.getInputStream());
            BlobRequestConditions blobRequestConditions = new BlobRequestConditions();
            blobRequestConditions.setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);
            blockBlobItemResponse = blobClient.uploadWithResponse(new BlobParallelUploadOptions(data).setRequestConditions(blobRequestConditions),
                null, Context.NONE);
        }
        BlockBlobItem value = blockBlobItemResponse.getValue();
        return new UploadResponse.Builder().withETag(value.getETag()).build();
    }

    @Override
    public Optional<ObjectStorageEntry> get(String key) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        AzureObjectStorageEntry storageEntry = null;
        if (blobClient.exists()) {
            storageEntry = new AzureObjectStorageEntry(blobClient);
        }
        return Optional.ofNullable(storageEntry);

    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        blobClient.getBlockBlobClient().delete();
    }

    /**
     * @return the configuration.
     */
    public AzureBlobContainerConfiguration getConfiguration() {
        return configuration;
    }
}
