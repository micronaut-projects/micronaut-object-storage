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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.function.Consumer;

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
public class AzureBlobStorageOperations
    implements ObjectStorageOperations<BlobParallelUploadOptions, Response<BlockBlobItem>> {

    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageOperations(@Parameter BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
    }

    @Override
    @NonNull
    public Response<BlockBlobItem> upload(@NonNull UploadRequest request) {
        BlobParallelUploadOptions options = getUploadOptions(request);
        return doUpload(request, options);
    }

    @Override
    @NonNull
    public Response<BlockBlobItem> upload(@NonNull UploadRequest request,
                                          @NonNull Consumer<BlobParallelUploadOptions> requestConsumer) {
        BlobParallelUploadOptions options = getUploadOptions(request);
        requestConsumer.accept(options);
        return doUpload(request, options);
    }

    @Override
    @NonNull
    @SuppressWarnings("java:S1854")
    public Optional<ObjectStorageEntry<?>> retrieve(@NonNull String key) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        AzureBlobStorageEntry storageEntry = null;
        if (TRUE.equals(blobClient.exists())) {
            BinaryData data = blobClient.getBlockBlobClient().downloadContent();
            storageEntry = new AzureBlobStorageEntry(key, data);
        }
        return Optional.ofNullable(storageEntry);

    }

    @Override
    public void delete(@NonNull String key) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        blobClient.getBlockBlobClient().delete();
    }

    @NonNull
    protected BlobParallelUploadOptions getUploadOptions(@NonNull UploadRequest request) {
        return new BlobParallelUploadOptions(request.getInputStream())
            .setRequestConditions(new BlobRequestConditions().setIfNoneMatch(ETAG_WILDCARD));
    }

    private Response<BlockBlobItem> doUpload(@NonNull UploadRequest request,
                                             @NonNull BlobParallelUploadOptions options) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(request.getKey());

        //TODO: make timeout configurable
        return blobClient.uploadWithResponse(options, null, Context.NONE);
    }

}
