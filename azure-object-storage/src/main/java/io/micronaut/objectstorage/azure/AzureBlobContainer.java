/*
 * Copyright 2021 original authors
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

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorage;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.UploadRequest;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * @author Pavol Gressa
 * @since 2.5
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
    public void put(UploadRequest uploadRequest) throws ObjectStorageException {
        final BinaryData data = BinaryData.fromStream(uploadRequest.getInputStream());
        final BlobClient blobClient = blobContainerClient.getBlobClient(uploadRequest.getKey());
        blobClient.upload(data, uploadRequest.isOverwrite());
    }

    @Override
    public Optional<ObjectStorageEntry> get(String objectPath) throws ObjectStorageException {
        final BlobClient blobClient = blobContainerClient.getBlobClient(objectPath);
        AzureObjectStorageEntry storageEntry = null;
        if (blobClient.exists()) {
            storageEntry = new AzureObjectStorageEntry(blobClient);
        }
        return Optional.ofNullable(storageEntry);

    }

    @Override
    public void delete(String path) throws ObjectStorageException {

    }

    public AzureBlobContainerConfiguration getConfiguration() {
        return configuration;
    }
}
