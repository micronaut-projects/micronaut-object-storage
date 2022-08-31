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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.UploadRequest;
import io.micronaut.objectstorage.UploadResponse;
import jakarta.inject.Singleton;

import java.util.Optional;

import static com.azure.storage.common.implementation.Constants.HeaderConstants.ETAG_WILDCARD;
import static java.lang.Boolean.TRUE;

/**
 * Azure implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(BlobContainerClient.class)
@Singleton
public class AzureBlobStorageOperations implements ObjectStorageOperations {

    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageOperations(@Parameter BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
    }

    @Override
    public UploadResponse upload(UploadRequest uploadRequest) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(uploadRequest.getKey());

        Response<BlockBlobItem> blockBlobItemResponse;
        if (uploadRequest instanceof UploadRequest.FileUploadRequest) {
            UploadRequest.FileUploadRequest fileUploadRequest = (UploadRequest.FileUploadRequest) uploadRequest;

            BlobRequestConditions requestConditions = new BlobRequestConditions().setIfNoneMatch(ETAG_WILDCARD);
            BlobUploadFromFileOptions options = new BlobUploadFromFileOptions(fileUploadRequest.getAbsolutePath())
                .setRequestConditions(requestConditions);
            blockBlobItemResponse = blobClient.uploadFromFileWithResponse(options, null, null);
        } else {
            BinaryData data = BinaryData.fromStream(uploadRequest.getInputStream());
            BlobRequestConditions blobRequestConditions = new BlobRequestConditions();
            blobRequestConditions.setIfNoneMatch(ETAG_WILDCARD);
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
        if (TRUE.equals(blobClient.exists())) {
            storageEntry = new AzureBlobStorageEntry(blobClient);
        }
        return Optional.ofNullable(storageEntry);

    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        blobClient.getBlockBlobClient().delete();
    }

}
