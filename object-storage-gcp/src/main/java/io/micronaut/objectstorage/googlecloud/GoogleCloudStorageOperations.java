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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Google Cloud implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(GoogleCloudStorageConfiguration.class)
public class GoogleCloudStorageOperations
    implements ObjectStorageOperations<BlobInfo.Builder, Blob, Boolean> {

    private final InputStreamMapper inputStreamMapper;
    private final Storage storage;
    private final GoogleCloudStorageConfiguration configuration;

    /**
     * Constructor.
     * @param configuration Google Storage Configuration
     * @param inputStreamMapper Input Stream Mapper
     * @param storage Interface for Google Cloud Storage
     */
    public GoogleCloudStorageOperations(@Parameter GoogleCloudStorageConfiguration configuration,
                                        InputStreamMapper inputStreamMapper,
                                        Storage storage) {
        this.inputStreamMapper = inputStreamMapper;
        this.storage = storage;
        this.configuration = configuration;
    }

    @Override
    @NonNull
    public UploadResponse<Blob> upload(@NonNull UploadRequest uploadRequest) {
        return upload(uploadRequest, createBlogInfoBuilder(uploadRequest).build());
    }

    @Override
    @NonNull
    public UploadResponse<Blob> upload(@NonNull UploadRequest uploadRequest,
                       @NonNull Consumer<BlobInfo.Builder> uploadRequestBuilder) {
        BlobInfo.Builder builder = createBlogInfoBuilder(uploadRequest);
        uploadRequestBuilder.accept(builder);
        return upload(uploadRequest, builder.build());
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<GoogleCloudStorageEntry> retrieve(@NonNull String key) {
        BlobId blobId = BlobId.of(configuration.getBucket(), key);
        Blob blob = storage.get(blobId);

        GoogleCloudStorageEntry storageEntry = null;
        if (blob != null && blob.exists()) {
            storageEntry = new GoogleCloudStorageEntry(blob);
        }
        return Optional.ofNullable(storageEntry);
    }

    @Override
    @NonNull
    public Boolean delete(@NonNull String key) {
        BlobId blobId = BlobId.of(configuration.getBucket(), key);
        return storage.delete(blobId);
    }

    /**
     *
     * @param uploadRequest Upload Request
     * @return BlobInfo Builder
     */
    @NonNull
    protected BlobInfo.Builder createBlogInfoBuilder(@NonNull UploadRequest uploadRequest) {
        BlobId blobId = BlobId.of(configuration.getBucket(), uploadRequest.getKey());
        return BlobInfo.newBuilder(blobId)
            .setContentType(uploadRequest.getContentType().orElse(null));
    }

    @NonNull
    private UploadResponse<Blob> upload(@NonNull UploadRequest uploadRequest, @NonNull BlobInfo blobInfo) {
        Blob blob = storage.create(blobInfo, inputStreamMapper.toByteArray(uploadRequest.getInputStream()));
        return UploadResponse.of(uploadRequest.getKey(), blob.getEtag(), blob);
    }
}
