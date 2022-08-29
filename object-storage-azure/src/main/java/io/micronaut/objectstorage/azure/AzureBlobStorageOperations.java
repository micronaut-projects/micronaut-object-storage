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
 * Azure implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(BlobContainerClient.class)
@Singleton
public class AzureBlobStorageOperations implements ObjectStorageOperations {

    private final AzureBlobStorageConfiguration configuration;
    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageOperations(@Parameter String name, BlobContainerClient blobContainerClient, BeanContext beanContext) {
        this.blobContainerClient = blobContainerClient;
        this.configuration = beanContext.getBean(AzureBlobStorageConfiguration.class, Qualifiers.byName(name));
    }

    @Override
    public UploadResponse upload(UploadRequest uploadRequest) throws ObjectStorageException {
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
    public Optional<ObjectStorageEntry> retrieve(String key) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        AzureBlobStorageEntry storageEntry = null;
        if (Boolean.TRUE.equals(blobClient.exists())) {
            storageEntry = new AzureBlobStorageEntry(blobClient);
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
    public AzureBlobStorageConfiguration getConfiguration() {
        return configuration;
    }
}
