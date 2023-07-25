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
import com.google.cloud.storage.StorageException;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Google Cloud implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(GoogleCloudStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = GoogleCloudStorageConfiguration.class)
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
        return upload(uploadRequest, createBlobInfoBuilder(uploadRequest).build());
    }

    @Override
    @NonNull
    public UploadResponse<Blob> upload(@NonNull UploadRequest uploadRequest,
                       @NonNull Consumer<BlobInfo.Builder> uploadRequestBuilder) {
        BlobInfo.Builder builder = createBlobInfoBuilder(uploadRequest);
        uploadRequestBuilder.accept(builder);
        return upload(uploadRequest, builder.build());
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<GoogleCloudStorageEntry> retrieve(@NonNull String key) {
        GoogleCloudStorageEntry storageEntry = null;
        BlobId blobId = BlobId.of(configuration.getBucket(), key);

        try {
            Blob blob = storage.get(blobId);

            if (blobExists(blob)) {
                storageEntry = new GoogleCloudStorageEntry(blob);
            }
            return Optional.ofNullable(storageEntry);
        } catch (StorageException e) {
            String msg = String.format("Error when trying to retrieve an object with key [%s] from Google Cloud Storage", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public Boolean delete(@NonNull String key) {
        BlobId blobId = BlobId.of(configuration.getBucket(), key);
        try {
            return storage.delete(blobId);
        } catch (StorageException e) {
            String msg = String.format("Error when trying to delete an object with key [%s] from Google Cloud Storage", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public boolean exists(@NonNull String key) {
        try {
            Blob blob = storage.get(BlobId.of(configuration.getBucket(), key));
            return blobExists(blob);
        } catch (StorageException e) {
            String msg = String.format("Error when checking the existence of an object with key [%s] from Google Cloud Storage", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @NonNull
    @Override
    public Set<String> listObjects() {
        String bucket = configuration.getBucket();
        try {
            Iterable<Blob> blobs = storage.list(bucket).iterateAll();
            return StreamSupport.stream(blobs.spliterator(), false)
                .map(BlobInfo::getName)
                .collect(Collectors.toSet());
        } catch (StorageException e) {
            String msg = String.format("Error when listing the objects of the Google Cloud Storage bucket [%s]", bucket);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        try {
            storage.copy(Storage.CopyRequest.of(configuration.getBucket(), sourceKey, destinationKey));
        } catch (StorageException e) {
            String msg = String.format("Error when copying the object with key [%s] to key [%s] in Google Cloud Storage", sourceKey, destinationKey);
            throw new ObjectStorageException(msg, e);
        }
    }

    /**
     *
     * @param uploadRequest Upload Request
     * @return BlobInfo Builder
     */
    @NonNull
    protected BlobInfo.Builder createBlobInfoBuilder(@NonNull UploadRequest uploadRequest) {
        BlobId blobId = BlobId.of(configuration.getBucket(), uploadRequest.getKey());
        BlobInfo.Builder builder = BlobInfo.newBuilder(blobId)
            .setContentType(uploadRequest.getContentType().orElse(null));
        if (CollectionUtils.isNotEmpty(uploadRequest.getMetadata())) {
            builder.setMetadata(uploadRequest.getMetadata());
        }
        return builder;
    }

    @NonNull
    private UploadResponse<Blob> upload(@NonNull UploadRequest uploadRequest, @NonNull BlobInfo blobInfo) {
        try {
            Blob blob = storage.create(blobInfo, inputStreamMapper.toByteArray(uploadRequest.getInputStream()));
            return UploadResponse.of(uploadRequest.getKey(), blob.getEtag(), blob);
        } catch (StorageException e) {
            throw new ObjectStorageException("Error when trying to upload an object to Google Cloud Storage", e);
        }
    }

    private boolean blobExists(Blob blob) {
        return blob != null && blob.exists();
    }
}
