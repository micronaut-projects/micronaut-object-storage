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
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.api.gax.paging.Page;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.PresignedUpload;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;

    private final Storage storage;
    private final GoogleCloudStorageConfiguration configuration;

    /**
     * Constructor.
     * @param configuration Google Storage Configuration
     * @param storage Interface for Google Cloud Storage
     */
    public GoogleCloudStorageOperations(@Parameter GoogleCloudStorageConfiguration configuration,
                                        Storage storage) {
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
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String bucket = configuration.getBucket();
        try {
            String continuationToken = null;
            do {
                ListObjectsResponse response = listObjects(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken));
                keys.addAll(response.getKeys());
                continuationToken = response.getContinuationToken().orElse(null);
            } while (continuationToken != null);
            return keys;
        } catch (StorageException e) {
            String msg = String.format("Error when listing the objects of the Google Cloud Storage bucket [%s]", bucket);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        String bucket = configuration.getBucket();
        try {
            List<Storage.BlobListOption> filteredOptions = new ArrayList<>();
            request.getPrefix().map(Storage.BlobListOption::prefix).ifPresent(filteredOptions::add);
            filteredOptions.add(Storage.BlobListOption.pageSize(request.getPageSize()));
            request.getContinuationToken().map(Storage.BlobListOption::pageToken).ifPresent(filteredOptions::add);
            Page<Blob> page = storage.list(bucket, filteredOptions.toArray(Storage.BlobListOption[]::new));
            List<String> keys = new ArrayList<>();
            for (Blob blob : page.getValues()) {
                keys.add(blob.getName());
            }
            return new ListObjectsResponse(keys, page.getNextPageToken());
        } catch (StorageException e) {
            String msg = String.format("Error when listing a page of objects of the Google Cloud Storage bucket [%s]", bucket);
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

    @Override
    @NonNull
    public Optional<PresignedUpload> createPresignedUpload(@NonNull CreatePresignedUploadRequest request) {
        try {
            BlobInfo blobInfo = createBlobInfoBuilder(request).build();
            Instant expiration = Instant.now().plus(request.getExpiresIn());
            Map<String, String> signedHeaders = buildSignedHeaders(request);
            List<Storage.SignUrlOption> options = new ArrayList<>();
            options.add(Storage.SignUrlOption.httpMethod(HttpMethod.PUT));
            options.add(Storage.SignUrlOption.withV4Signature());
            if (request.getContentType().isPresent()) {
                options.add(Storage.SignUrlOption.withContentType());
            }
            if (CollectionUtils.isNotEmpty(signedHeaders)) {
                options.add(Storage.SignUrlOption.withExtHeaders(signedHeaders));
            }
            URL url = storage.signUrl(
                blobInfo,
                request.getExpiresIn().toMillis(),
                TimeUnit.MILLISECONDS,
                options.toArray(Storage.SignUrlOption[]::new)
            );
            return Optional.of(new PresignedUpload(
                java.net.URI.create(url.toString()),
                "PUT",
                toPortableHeaders(request, signedHeaders),
                expiration
            ));
        } catch (RuntimeException e) {
            String msg = String.format(
                "Error when trying to create a pre-signed upload request with key [%s] in Google Cloud Storage",
                request.getKey()
            );
            throw new ObjectStorageException(msg, e);
        }
    }

    /**
     *
     * @param uploadRequest Upload Request
     * @return BlobInfo Builder
     */
    protected BlobInfo.@NonNull Builder createBlobInfoBuilder(@NonNull UploadRequest uploadRequest) {
        BlobId blobId = BlobId.of(configuration.getBucket(), uploadRequest.getKey());
        BlobInfo.Builder builder = BlobInfo.newBuilder(blobId)
            .setContentType(uploadRequest.getContentType().orElse(null));
        if (CollectionUtils.isNotEmpty(uploadRequest.getMetadata())) {
            builder.setMetadata(uploadRequest.getMetadata());
        }
        return builder;
    }

    /**
     * @param request the pre-signed upload request
     * @return BlobInfo Builder
     * @since 3.1.0
     */
    protected BlobInfo.@NonNull Builder createBlobInfoBuilder(@NonNull CreatePresignedUploadRequest request) {
        BlobId blobId = BlobId.of(configuration.getBucket(), request.getKey());
        BlobInfo.Builder builder = BlobInfo.newBuilder(blobId)
            .setContentType(request.getContentType().orElse(null));
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            builder.setMetadata(request.getMetadata());
        }
        return builder;
    }

    @NonNull
    private UploadResponse<Blob> upload(@NonNull UploadRequest uploadRequest, @NonNull BlobInfo blobInfo) {
        try (InputStream inputStream = uploadRequest.getInputStream()) {
            Blob blob = storage.createFrom(blobInfo, inputStream);
            return UploadResponse.of(uploadRequest.getKey(), blob.getEtag(), blob);
        } catch (Exception e) {
            throw new ObjectStorageException("Error when trying to upload an object to Google Cloud Storage", e);
        }
    }

    private boolean blobExists(Blob blob) {
        return blob != null && blob.exists();
    }

    @NonNull
    private Map<String, String> buildSignedHeaders(@NonNull CreatePresignedUploadRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.getContentLength().ifPresent(length -> headers.put("Content-Length", Long.toString(length)));
        request.getMetadata().forEach((key, value) -> headers.put("x-goog-meta-" + key, value));
        return headers;
    }

    @NonNull
    private Map<String, List<String>> toPortableHeaders(@NonNull CreatePresignedUploadRequest request,
                                                        @NonNull Map<String, String> signedHeaders) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getContentType().ifPresent(contentType -> headers.put("Content-Type", List.of(contentType)));
        signedHeaders.forEach((name, value) -> headers.put(name, List.of(value)));
        return headers;
    }
}
