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
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
@Requires(beans = BlobContainerClient.class)
@Requires(condition = AzureBlobStorageEnabledCondition.class)
public class AzureBlobStorageOperations
    implements ObjectStorageOperations<BlobParallelUploadOptions, BlockBlobItem, Response<Void>> {

    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageOperations(@Parameter BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
    }

    @Override
    @NonNull
    public UploadResponse<BlockBlobItem> upload(@NonNull UploadRequest request) {
        BlobParallelUploadOptions options = getUploadOptions(request);
        return doUpload(request, options);
    }

    @Override
    @NonNull
    public UploadResponse<BlockBlobItem> upload(@NonNull UploadRequest request,
                                          @NonNull Consumer<BlobParallelUploadOptions> requestConsumer) {
        BlobParallelUploadOptions options = getUploadOptions(request);
        requestConsumer.accept(options);
        return doUpload(request, options);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<AzureBlobStorageEntry> retrieve(@NonNull String key) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        AzureBlobStorageEntry storageEntry = null;
        if (TRUE.equals(blobClient.exists())) {
            try {
                BinaryData data = blobClient.getBlockBlobClient().downloadContent();
                BlobProperties blobProperties = blobClient.getProperties();
                storageEntry = new AzureBlobStorageEntry(key, data, blobProperties);
            } catch (UncheckedIOException e) {
                throw new ObjectStorageException("Error when trying to retrieve a file from Azure Blob Storage", e);
            }
        }
        return Optional.ofNullable(storageEntry);

    }

    @Override
    @NonNull
    public Response<Void> delete(@NonNull String key) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        return blobClient.getBlockBlobClient().deleteWithResponse(null, null, null, Context.NONE);
    }

    @Override
    public boolean exists(@NonNull String key) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(key);
        return blobClient.exists();
    }

    @NonNull
    @Override
    public Set<String> listObjects() {
        Set<String> keys = new LinkedHashSet<>();
        String continuationToken = null;
        do {
            ListObjectsResponse response = listObjects(new ListObjectsRequest(1000, null, continuationToken));
            keys.addAll(response.getKeys());
            continuationToken = response.getContinuationToken().orElse(null);
        } while (continuationToken != null);
        return keys;
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        Iterator<PagedResponse<BlobItem>> pages = listNativeBlobs(
            request.getPrefix().orElse(null),
            request.getContinuationToken().orElse(null),
            request.getPageSize()
        ).iterator();
        if (!pages.hasNext()) {
            return new ListObjectsResponse(Collections.emptyList());
        }
        PagedResponse<BlobItem> page = pages.next();
        List<String> keys = new ArrayList<>();
        for (BlobItem blobItem : page.getElements()) {
            keys.add(blobItem.getName());
        }
        return new ListObjectsResponse(keys, page.getContinuationToken());
    }

    /**
     * Returns Azure-native paged blob responses for the requested prefix, continuation token, and page size.
     * Subclasses may override this method only to customize page retrieval while preserving the Azure token and
     * service-order semantics expected by {@link #listObjects(io.micronaut.objectstorage.request.ListObjectsRequest)}.
     *
     * @param prefix The raw key prefix filter, or {@code null} when no prefix filtering is requested
     * @param continuationToken The opaque Azure continuation token, or {@code null} for the first page
     * @param pageSize The requested maximum page size
     * @return The Azure-native paged responses backing the current listing request
     * @since 3.0.0
     */
    @NonNull
    protected Iterable<PagedResponse<BlobItem>> listNativeBlobs(String prefix, String continuationToken, int pageSize) {
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
        return blobContainerClient.listBlobs(options, null).iterableByPage(continuationToken, pageSize);
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        final BlobClient sourceBlobClient = blobContainerClient.getBlobClient(sourceKey);
        final BlobClient destinationBlobClient = blobContainerClient.getBlobClient(destinationKey);

        destinationBlobClient.copyFromUrl(sourceBlobClient.getBlobUrl());
    }

    /**
     * @param request the upload request
     * @return An Azure's {@link BlobParallelUploadOptions} from a Micronaut's {@link UploadRequest}.
     */
    @NonNull
    protected BlobParallelUploadOptions getUploadOptions(@NonNull UploadRequest request) {
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(request.getInputStream())
            .setRequestConditions(new BlobRequestConditions().setIfNoneMatch(ETAG_WILDCARD));
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            options.setMetadata(request.getMetadata());
        }
        if (request.getContentType().isPresent()) {
            BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(request.getContentType().get());
            options.setHeaders(headers);
        }
        return options;
    }

    private UploadResponse<BlockBlobItem> doUpload(@NonNull UploadRequest request,
                                             @NonNull BlobParallelUploadOptions options) {
        final BlobClient client = blobContainerClient.getBlobClient(request.getKey());
        BlockBlobItem item;
        if (request.getContentSize().isPresent()) {
            long length = request.getContentSize().get();
            InputStream inputStream = Optional.ofNullable(options.getDataStream()).orElse(request.getInputStream());
            BlockBlobSimpleUploadOptions simpleUploadOptions = toBlockBlobSimpleUploadOptions(options, inputStream, length);
            item = client
                .getBlockBlobClient()
                .uploadWithResponse(simpleUploadOptions, null, Context.NONE)
                .getValue();
        } else {
            client.deleteIfExists();
            item = client.uploadWithResponse(options, null, Context.NONE).getValue();
        }

        //TODO: make timeout configurable
        return UploadResponse.of(request.getKey(), item.getETag(), item);
    }

    private BlockBlobSimpleUploadOptions toBlockBlobSimpleUploadOptions(@NonNull BlobParallelUploadOptions options, @NonNull InputStream inputStream, @NonNull long length) {
        BlockBlobSimpleUploadOptions simpleUploadOptions =
            new BlockBlobSimpleUploadOptions(new BufferedInputStream(inputStream), length);
        simpleUploadOptions.setMetadata(options.getMetadata());
        simpleUploadOptions.setHeaders(options.getHeaders());
        return simpleUploadOptions;
    }

}
