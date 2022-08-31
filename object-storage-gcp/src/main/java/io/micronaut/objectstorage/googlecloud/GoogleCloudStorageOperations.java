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
package io.micronaut.objectstorage.googlecloud;


import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.UploadRequest;
import io.micronaut.objectstorage.UploadResponse;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Google Cloud implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(GoogleCloudStorageConfiguration.class)
public class GoogleCloudStorageOperations implements ObjectStorageOperations<BlobInfo.Builder, Blob> {

    private final InputStreamMapper inputStreamMapper;
    private final Storage storage;
    private final GoogleCloudStorageConfiguration configuration;

    public GoogleCloudStorageOperations(InputStreamMapper inputStreamMapper,
                                        Storage storage,
                                        GoogleCloudStorageConfiguration configuration) {
        this.inputStreamMapper = inputStreamMapper;
        this.storage = storage;
        this.configuration = configuration;
    }

    @Override
    @NonNull
    public Blob upload(@NonNull UploadRequest uploadRequest) throws ObjectStorageException {
        return upload(uploadRequest, createBlogInfoBuilder(uploadRequest).build());
    }

    @Override
    @NonNull
    public Blob upload(@NonNull UploadRequest uploadRequest, @NonNull Consumer<BlobInfo.Builder> uploadRequestBuilder) throws ObjectStorageException {
        BlobInfo.Builder builder = createBlogInfoBuilder(uploadRequest);
        uploadRequestBuilder.accept(builder);
        return upload(uploadRequest, builder.build());
    }

    @Override
    public Optional<ObjectStorageEntry> retrieve(String key) throws ObjectStorageException {
        BlobId blobId = BlobId.of(configuration.getName(), key);
        Blob blob = storage.get(blobId);

        GoogleCloudStorageEntry storageEntry = null;
        if (blob != null && blob.exists()) {
            storageEntry = new GoogleCloudStorageEntry(blob);
        }
        return Optional.ofNullable(storageEntry);
    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        BlobId blobId = BlobId.of(configuration.getName(), key);
        storage.delete(blobId);
    }

    /**
     *
     * @param uploadRequest Upload Request
     * @return BlobInfo Builder
     */
    @NonNull
    protected BlobInfo.Builder createBlogInfoBuilder(@NonNull UploadRequest uploadRequest) {
        BlobId blobId = BlobId.of(configuration.getName(), uploadRequest.getKey());
        return BlobInfo.newBuilder(blobId)
            .setContentType(uploadRequest.getContentType().orElse(null));
    }

    @NonNull
    private Blob upload(@NonNull UploadRequest uploadRequest, @NonNull BlobInfo blobInfo) throws ObjectStorageException {
        return storage.create(blobInfo, inputStreamMapper.toByteArray(uploadRequest.getInputStream()));
    }
}
