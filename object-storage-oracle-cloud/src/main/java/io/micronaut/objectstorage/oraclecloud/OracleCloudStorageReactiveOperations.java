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
package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.auth.RegionProvider;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageAsyncClient;
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.responses.AsyncHandler;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reactive Oracle Cloud object storage operations.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(OracleCloudStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = ObjectStorageAsyncClient.class)
public final class OracleCloudStorageReactiveOperations implements ReactiveObjectStorageOperations<
    PutObjectRequest.Builder,
    PutObjectResponse,
    DeleteObjectResponse> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;
    private static final Logger LOG = LoggerFactory.getLogger(OracleCloudStorageReactiveOperations.class);

    private final OracleCloudStorageConfiguration configuration;
    private final ObjectStorageAsyncClient objectStorageAsyncClient;
    private final RegionProvider regionProvider;

    public OracleCloudStorageReactiveOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                                ObjectStorageAsyncClient objectStorageAsyncClient,
                                                RegionProvider regionProvider) {
        this.configuration = configuration;
        this.objectStorageAsyncClient = objectStorageAsyncClient;
        this.regionProvider = regionProvider;
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<PutObjectResponse>> upload(@NonNull UploadRequest request) {
        return upload(request, builder -> {
        });
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<PutObjectResponse>> upload(@NonNull UploadRequest request,
                                                               @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        PutObjectRequest putObjectRequest = builder.build();
        return Publishers.fromCompletableFuture(() -> putObject(putObjectRequest)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw uploadException(request.getKey(), throwable);
                }
                return UploadResponse.of(request.getKey(), response.getETag(), response);
            }));
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <E extends ObjectStorageEntry<?>> Publisher<Optional<E>> retrieve(@NonNull String key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .objectName(key)
            .build();
        return Publishers.fromCompletableFuture(() -> getObject(request)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof BmcException e) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Error when trying to retrieve an object from Oracle Cloud Storage: {}", e.getMessage(), e);
                        }
                        return Optional.<E>empty();
                    }
                    throw retrievalException(key, cause);
                }
                return Optional.of((E) new OracleCloudStorageEntry(key, response));
            }));
    }

    @Override
    @NonNull
    public Publisher<DeleteObjectResponse> delete(@NonNull String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .objectName(key)
            .build();
        return Publishers.fromCompletableFuture(() -> deleteObject(request)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw deleteException(key, throwable);
                }
                return response;
            }));
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .objectName(key)
            .build();
        return Publishers.fromCompletableFuture(() -> headObject(request)
            .handle((ignored, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof BmcException) {
                        return false;
                    }
                    throw existsException(key, cause);
                }
                return true;
            }));
    }

    @Override
    @NonNull
    public Publisher<Set<String>> listObjects() {
        return Publishers.fromCompletableFuture(() -> collectKeys(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE), new LinkedHashSet<>())
            .handle((result, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof BmcException e) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Error when listing the objects in the bucket {} from Oracle Cloud Storage: {}", configuration.getBucket(), e.getMessage(), e);
                        }
                        return Collections.<String>emptySet();
                    }
                    throw listException(cause);
                }
                return result;
            }));
    }

    @Override
    @NonNull
    public Publisher<ListObjectsResponse> listObjects(@NonNull ListObjectsRequest request) {
        com.oracle.bmc.objectstorage.requests.ListObjectsRequest listRequest = getListObjectsRequest(request);
        return Publishers.fromCompletableFuture(() -> listObjects(listRequest)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof BmcException e) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Error when listing the objects in the bucket {} from Oracle Cloud Storage: {}", configuration.getBucket(), e.getMessage(), e);
                        }
                        return new ListObjectsResponse(Collections.emptyList());
                    }
                    throw listException(cause);
                }
                return toListObjectsResponse(response);
            }));
    }

    @Override
    @NonNull
    public Publisher<Void> copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        CopyObjectRequest request = CopyObjectRequest.builder()
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .copyObjectDetails(CopyObjectDetails.builder()
                .sourceObjectName(sourceKey)
                .destinationObjectName(destinationKey)
                .destinationBucket(configuration.getBucket())
                .destinationNamespace(configuration.getNamespace())
                .destinationRegion(regionProvider.getRegion().getRegionId())
                .build())
            .build();
        return Publishers.onComplete(
            Publishers.empty(),
            () -> copyObject(request)
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw copyException(sourceKey, destinationKey, throwable);
                    }
                    return null;
                })
        );
    }

    private PutObjectRequest.@NonNull Builder getRequestBuilder(@NonNull UploadRequest request) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .objectName(request.getKey())
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .putObjectBody(request.getInputStream());

        request.getContentSize().ifPresent(builder::contentLength);
        request.getContentType().ifPresent(builder::contentType);
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            builder.opcMeta(request.getMetadata());
        }
        return builder;
    }

    private com.oracle.bmc.objectstorage.requests.ListObjectsRequest getListObjectsRequest(ListObjectsRequest request) {
        return com.oracle.bmc.objectstorage.requests.ListObjectsRequest.builder()
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .prefix(request.getPrefix().orElse(null))
            .limit(request.getPageSize())
            .startAfter(request.getContinuationToken().orElse(null))
            .build();
    }

    private CompletableFuture<Set<String>> collectKeys(ListObjectsRequest request, LinkedHashSet<String> keys) {
        com.oracle.bmc.objectstorage.requests.ListObjectsRequest listRequest = getListObjectsRequest(request);
        return listObjects(listRequest)
            .thenCompose(response -> {
                List<String> pageKeys = response.getListObjects()
                    .getObjects()
                    .stream()
                    .map(ObjectSummary::getName)
                    .toList();
                keys.addAll(pageKeys);
                String continuationToken = response.getListObjects().getNextStartWith();
                if (continuationToken == null) {
                    return CompletableFuture.completedFuture(keys);
                }
                return collectKeys(
                    new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, request.getPrefix().orElse(null), continuationToken),
                    keys
                );
            })
            .thenApply(set -> (Set<String>) set);
    }

    private ListObjectsResponse toListObjectsResponse(com.oracle.bmc.objectstorage.responses.ListObjectsResponse response) {
        List<String> keys = response.getListObjects()
            .getObjects()
            .stream()
            .map(ObjectSummary::getName)
            .toList();
        return new ListObjectsResponse(keys, response.getListObjects().getNextStartWith());
    }

    private RuntimeException uploadException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to upload an object to Oracle Cloud Storage with key [%s]", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException retrievalException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to retrieve an object from Oracle Cloud Storage with key [%s]", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException deleteException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to delete an object from Oracle Cloud Storage with key [%s]", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException existsException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to check the existence of an object in Oracle Cloud Storage with key [%s]", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException listException(Throwable throwable) {
        String msg = String.format("Error when listing the objects in the bucket [%s] from Oracle Cloud Storage", configuration.getBucket());
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException copyException(String sourceKey, String destinationKey, Throwable throwable) {
        String msg = String.format(
            "Error when copying a file from the key [%s] to the key [%s] in the Oracle Cloud Storage bucket [%s]",
            sourceKey,
            destinationKey,
            configuration.getBucket()
        );
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException toObjectStorageException(String message, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ObjectStorageException exception) {
            return exception;
        }
        if (cause instanceof BmcException bmcException) {
            return new ObjectStorageException(message, bmcException);
        }
        if (cause instanceof RuntimeException exception) {
            return exception;
        }
        return new ObjectStorageException(message, cause);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && !(current instanceof BmcException) && !(current instanceof ObjectStorageException)) {
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private static <REQ, RES> CompletableFuture<RES> toCompletableFuture(AsyncCall<REQ, RES> call) {
        CompletableFuture<RES> future = new CompletableFuture<>();
        call.invoke(new AsyncHandler<>() {
            @Override
            public void onSuccess(REQ request, RES response) {
                future.complete(response);
            }

            @Override
            public void onError(REQ request, Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private CompletableFuture<PutObjectResponse> putObject(PutObjectRequest request) {
        return toCompletableFuture((AsyncHandler<PutObjectRequest, PutObjectResponse> handler) ->
            objectStorageAsyncClient.putObject(request, handler)
        );
    }

    private CompletableFuture<GetObjectResponse> getObject(GetObjectRequest request) {
        return toCompletableFuture((AsyncHandler<GetObjectRequest, GetObjectResponse> handler) ->
            objectStorageAsyncClient.getObject(request, handler)
        );
    }

    private CompletableFuture<DeleteObjectResponse> deleteObject(DeleteObjectRequest request) {
        return toCompletableFuture((AsyncHandler<DeleteObjectRequest, DeleteObjectResponse> handler) ->
            objectStorageAsyncClient.deleteObject(request, handler)
        );
    }

    private CompletableFuture<com.oracle.bmc.objectstorage.responses.HeadObjectResponse> headObject(HeadObjectRequest request) {
        return toCompletableFuture((AsyncHandler<HeadObjectRequest, com.oracle.bmc.objectstorage.responses.HeadObjectResponse> handler) ->
            objectStorageAsyncClient.headObject(request, handler)
        );
    }

    private CompletableFuture<com.oracle.bmc.objectstorage.responses.ListObjectsResponse> listObjects(
        com.oracle.bmc.objectstorage.requests.ListObjectsRequest request) {
        return toCompletableFuture((AsyncHandler<com.oracle.bmc.objectstorage.requests.ListObjectsRequest, com.oracle.bmc.objectstorage.responses.ListObjectsResponse> handler) ->
            objectStorageAsyncClient.listObjects(request, handler)
        );
    }

    private CompletableFuture<com.oracle.bmc.objectstorage.responses.CopyObjectResponse> copyObject(CopyObjectRequest request) {
        return toCompletableFuture((AsyncHandler<CopyObjectRequest, com.oracle.bmc.objectstorage.responses.CopyObjectResponse> handler) ->
            objectStorageAsyncClient.copyObject(request, handler)
        );
    }

    @FunctionalInterface
    private interface AsyncCall<REQ, RES> {
        void invoke(AsyncHandler<REQ, RES> handler);
    }
}
