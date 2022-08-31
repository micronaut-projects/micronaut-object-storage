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

import com.azure.core.http.RequestConditions;
import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.UploadRequest;
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
public class AzureBlobStorageOperations implements ObjectStorageOperations<RequestConditions, Response<BlockBlobItem>> {

    private final AzureBlobStorageConfiguration configuration;
    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageOperations(@Parameter String name,
                                      BlobContainerClient blobContainerClient,
                                      BeanContext beanContext) {
        this.blobContainerClient = blobContainerClient;
        this.configuration = beanContext.getBean(AzureBlobStorageConfiguration.class, Qualifiers.byName(name));
    }

    @Override
    @NonNull
    public Response<BlockBlobItem> upload(@NonNull UploadRequest uploadRequest) throws ObjectStorageException {
        BlobRequestConditions requestConditions = uploadBlobRequestConditions();
        return upload(uploadRequest, requestConditions);
    }

    @Override
    public Response<BlockBlobItem> upload(@NonNull UploadRequest uploadRequest,
                                          @NonNull Consumer<RequestConditions> uploadRequestBuilder) throws ObjectStorageException {
        BlobRequestConditions requestConditions = uploadBlobRequestConditions();
        uploadRequestBuilder.accept(requestConditions);
        return upload(uploadRequest, requestConditions);
    }

    @NonNull
    private Response<BlockBlobItem> upload(@NonNull UploadRequest uploadRequest,
                                            BlobRequestConditions requestConditions) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(uploadRequest.getKey());
        if (uploadRequest instanceof UploadRequest.FileUploadRequest) {
            UploadRequest.FileUploadRequest fileUploadRequest = (UploadRequest.FileUploadRequest) uploadRequest;
            BlobUploadFromFileOptions options = new BlobUploadFromFileOptions(fileUploadRequest.getAbsolutePath())
                .setRequestConditions(requestConditions);
            return blobClient.uploadFromFileWithResponse(options, null, null);
        }

        BinaryData data = BinaryData.fromStream(uploadRequest.getInputStream());
        return blobClient.uploadWithResponse(new BlobParallelUploadOptions(data).setRequestConditions(requestConditions),
            null, Context.NONE);
    }

    @NonNull
    private BlobRequestConditions uploadBlobRequestConditions() {
        return new BlobRequestConditions().setIfNoneMatch(ETAG_WILDCARD);
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

    /**
     * @return the configuration.
     */
    public AzureBlobStorageConfiguration getConfiguration() {
        return configuration;
    }
}
