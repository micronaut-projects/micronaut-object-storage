/*
 * Copyright 2017-2026 original authors
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

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobAsyncClient;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reactive Azure Blob Storage operations backed by Azure async blob clients.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(BlobContainerAsyncClient.class)
@Requires(beans = BlobContainerAsyncClient.class)
@Requires(condition = AzureBlobStorageEnabledCondition.class)
public final class AzureBlobStorageReactiveOperations implements ReactiveObjectStorageOperations<
    BlobParallelUploadOptions,
    BlockBlobItem,
    Response<Void>> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;

    private final BlobContainerAsyncClient blobContainerAsyncClient;

    public AzureBlobStorageReactiveOperations(@Parameter BlobContainerAsyncClient blobContainerAsyncClient) {
        this.blobContainerAsyncClient = blobContainerAsyncClient;
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<BlockBlobItem>> upload(@NonNull UploadRequest request) {
        return doUpload(request, getUploadOptions(request));
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<BlockBlobItem>> upload(@NonNull UploadRequest request,
                                                           @NonNull Consumer<BlobParallelUploadOptions> requestConsumer) {
        BlobParallelUploadOptions options = getUploadOptions(request);
        requestConsumer.accept(options);
        return doUpload(request, options);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <E extends ObjectStorageEntry<?>> Publisher<Optional<E>> retrieve(@NonNull String key) {
        BlobAsyncClient blobAsyncClient = blobContainerAsyncClient.getBlobAsyncClient(key);
        return blobAsyncClient.exists()
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(Optional.<E>empty());
                }
                return Mono.zip(
                    blobAsyncClient.downloadContent(),
                    blobAsyncClient.getProperties()
                ).map(tuple -> Optional.of((E) new AzureBlobStorageEntry(key, tuple.getT1(), tuple.getT2())));
            })
            .onErrorResume(throwable -> isNotFound(throwable) ? Mono.just(Optional.empty()) : Mono.error(retrieveException(throwable)));
    }

    @Override
    @NonNull
    public Publisher<Response<Void>> delete(@NonNull String key) {
        return blobContainerAsyncClient.getBlobAsyncClient(key)
            .deleteWithResponse(null, null)
            .onErrorMap(this::deleteException);
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String key) {
        return blobContainerAsyncClient.getBlobAsyncClient(key)
            .exists()
            .onErrorMap(this::existsException);
    }

    @Override
    @NonNull
    public Publisher<Set<String>> listObjects() {
        return firstPage(listNativeBlobs(null), null, DEFAULT_LIST_PAGE_SIZE)
            .concatMapIterable(PagedResponse::getElements)
            .map(BlobItem::getName)
            .collect(LinkedHashSet<String>::new, LinkedHashSet::add)
            .map(set -> (Set<String>) set)
            .onErrorMap(this::listException);
    }

    @Override
    @NonNull
    public Publisher<ListObjectsResponse> listObjects(@NonNull ListObjectsRequest request) {
        return firstPage(
            listNativeBlobs(request.getPrefix().orElse(null)),
            request.getContinuationToken().orElse(null),
            request.getPageSize()
        )
            .next()
            .map(this::toListObjectsResponse)
            .switchIfEmpty(Mono.just(new ListObjectsResponse(Collections.emptyList())))
            .onErrorMap(this::listException);
    }

    @Override
    @NonNull
    public Publisher<Void> copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        BlobAsyncClient sourceBlobClient = blobContainerAsyncClient.getBlobAsyncClient(sourceKey);
        BlobAsyncClient destinationBlobClient = blobContainerAsyncClient.getBlobAsyncClient(destinationKey);
        return destinationBlobClient.copyFromUrl(sourceBlobClient.getBlobUrl())
            .then()
            .onErrorMap(this::copyException);
    }

    private @NonNull BlobParallelUploadOptions getUploadOptions(@NonNull UploadRequest request) {
        Optional<Long> contentSize = request.getContentSize();
        BlobParallelUploadOptions options = contentSize.isPresent()
            ? new BlobParallelUploadOptions(request.getInputStream(), contentSize.get())
            : new BlobParallelUploadOptions(request.getInputStream());
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            options.setMetadata(request.getMetadata());
        }
        Optional<String> contentType = request.getContentType();
        if (contentType.isPresent()) {
            BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(contentType.get());
            options.setHeaders(headers);
        }
        return options;
    }

    private @NonNull PagedFlux<BlobItem> listNativeBlobs(String prefix) {
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
        return blobContainerAsyncClient.listBlobs(options, null);
    }

    private Flux<PagedResponse<BlobItem>> firstPage(@NonNull PagedFlux<BlobItem> pagedFlux,
                                                    String continuationToken,
                                                    int pageSize) {
        return continuationToken == null ? pagedFlux.byPage(pageSize) : pagedFlux.byPage(continuationToken, pageSize);
    }

    private Mono<UploadResponse<BlockBlobItem>> doUpload(@NonNull UploadRequest request,
                                                         @NonNull BlobParallelUploadOptions options) {
        BlobAsyncClient blobAsyncClient = blobContainerAsyncClient.getBlobAsyncClient(request.getKey());
        Optional<Long> contentSize = request.getContentSize();
        Mono<BlockBlobItem> upload = contentSize.isPresent()
            ? blobAsyncClient.getBlockBlobAsyncClient()
                .uploadWithResponse(toBlockBlobSimpleUploadOptions(options, request.getInputStream(), contentSize.get()))
                .map(Response::getValue)
            : blobAsyncClient.deleteIfExists()
                .then(blobAsyncClient.uploadWithResponse(options).map(Response::getValue));
        return upload
            .map(item -> UploadResponse.of(request.getKey(), item.getETag(), item))
            .onErrorMap(this::uploadException);
    }

    private ListObjectsResponse toListObjectsResponse(PagedResponse<BlobItem> page) {
        List<String> keys = new java.util.ArrayList<>();
        for (BlobItem blobItem : page.getElements()) {
            keys.add(blobItem.getName());
        }
        return new ListObjectsResponse(keys, page.getContinuationToken());
    }

    private BlockBlobSimpleUploadOptions toBlockBlobSimpleUploadOptions(@NonNull BlobParallelUploadOptions options,
                                                                        @NonNull InputStream inputStream,
                                                                        long length) {
        BlockBlobSimpleUploadOptions simpleUploadOptions = new BlockBlobSimpleUploadOptions(new BufferedInputStream(inputStream), length);
        simpleUploadOptions.setMetadata(options.getMetadata());
        simpleUploadOptions.setHeaders(options.getHeaders());
        simpleUploadOptions.setRequestConditions(options.getRequestConditions());
        return simpleUploadOptions;
    }

    private RuntimeException uploadException(Throwable throwable) {
        return new ObjectStorageException("Error when trying to upload a file to Azure Blob Storage", throwable);
    }

    private RuntimeException retrieveException(Throwable throwable) {
        return new ObjectStorageException("Error when trying to retrieve a file from Azure Blob Storage", throwable);
    }

    private RuntimeException deleteException(Throwable throwable) {
        return new ObjectStorageException("Error when trying to delete a file from Azure Blob Storage", throwable);
    }

    private RuntimeException existsException(Throwable throwable) {
        return new ObjectStorageException("Error when trying to check the existence of a file in Azure Blob Storage", throwable);
    }

    private RuntimeException listException(Throwable throwable) {
        return new ObjectStorageException("Error when listing the objects in Azure Blob Storage", throwable);
    }

    private RuntimeException copyException(Throwable throwable) {
        return new ObjectStorageException("Error when trying to copy a file in Azure Blob Storage", throwable);
    }

    private boolean isNotFound(Throwable throwable) {
        return throwable instanceof com.azure.storage.blob.models.BlobStorageException exception
            && exception.getStatusCode() == 404;
    }
}
