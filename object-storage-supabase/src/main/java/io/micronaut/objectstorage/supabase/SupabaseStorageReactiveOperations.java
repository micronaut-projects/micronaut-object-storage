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
package io.micronaut.objectstorage.supabase;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.json.JsonMapper;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.supabase.SupabaseConfiguration;
import io.micronaut.supabase.storage.CopyObjectRequest;
import io.micronaut.supabase.storage.CopyObjectResponse;
import io.micronaut.supabase.storage.ListObjectsV2Request;
import io.micronaut.supabase.storage.ObjectSchema;
import io.micronaut.supabase.storage.ReactiveSupabaseStorageClient;
import io.micronaut.supabase.storage.UploadObjectResponse;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reactive Supabase Storage operations, built on the non-blocking Micronaut Supabase
 * {@link ReactiveSupabaseStorageClient}.
 *
 * <p>Requests authenticate with the bearer token returned by {@link #getAuthorization()}: by default the
 * {@code supabase.secret-key} (service role, bypasses Row Level Security) or, when absent, the
 * {@code supabase.publishable-key}. Override that method to use another token, such as the access token of
 * the current user.</p>
 *
 * <p>Nothing is sent until a returned publisher is subscribed to, and every failure, including a missing key or a
 * malformed continuation token, is signalled as an error. The only blocking step, reading the content of an upload
 * request that is not already in memory, runs on the Micronaut blocking executor.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
@EachBean(SupabaseStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = SupabaseStorageConfiguration.class)
@Requires(beans = ReactiveSupabaseStorageClient.class)
public class SupabaseStorageReactiveOperations implements ReactiveObjectStorageOperations<
    SupabaseStorageUploadRequest,
    UploadObjectResponse,
    List<ObjectSchema>> {

    private final SupabaseStorageConfiguration configuration;
    private final ReactiveSupabaseStorageClient client;
    private final SupabaseConfiguration supabaseConfiguration;
    private final InputStreamMapper inputStreamMapper;
    private final JsonMapper jsonMapper;
    private final Scheduler blockingScheduler;

    /**
     * @param configuration Object storage configuration
     * @param client Reactive Supabase Storage API client
     * @param supabaseConfiguration Supabase project configuration
     * @param inputStreamMapper InputStream Mapper
     * @param jsonMapper JSON mapper, used to encode user metadata
     * @param blockingExecutor The executor upload request contents are read on
     */
    public SupabaseStorageReactiveOperations(@Parameter SupabaseStorageConfiguration configuration,
                                             ReactiveSupabaseStorageClient client,
                                             SupabaseConfiguration supabaseConfiguration,
                                             InputStreamMapper inputStreamMapper,
                                             JsonMapper jsonMapper,
                                             @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        this.configuration = configuration;
        this.client = client;
        this.supabaseConfiguration = supabaseConfiguration;
        this.inputStreamMapper = inputStreamMapper;
        this.jsonMapper = jsonMapper;
        this.blockingScheduler = Schedulers.fromExecutorService(blockingExecutor);
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<UploadObjectResponse>> upload(@NonNull UploadRequest request) {
        return upload(request, supabaseRequest -> { });
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<UploadObjectResponse>> upload(@NonNull UploadRequest request,
                                                                  @NonNull Consumer<SupabaseStorageUploadRequest> requestConsumer) {
        return body(request)
            .map(body -> {
                SupabaseStorageUploadRequest supabaseRequest = getRequestBuilder(request, body);
                requestConsumer.accept(supabaseRequest);
                return supabaseRequest;
            })
            .flatMap(supabaseRequest -> withAuthorization(authorization -> {
                String key = supabaseRequest.getObjectKey();
                String bucket = supabaseRequest.getBucketName();
                String metadata = encodeMetadata(supabaseRequest.getMetadata());
                return SupabaseStorageCalls.executeReactive(
                        String.format("Error when trying to upload a file with key [%s] to Supabase Storage", key),
                        () -> client.uploadObject(authorization, bucket, key, supabaseRequest.getContentType(),
                            supabaseRequest.getCacheControl(), supabaseRequest.isUpsert(), metadata, supabaseRequest.getBody()))
                    .flatMap(upload -> SupabaseStorageCalls.executeReactive(
                            String.format("Error when trying to read the information of the uploaded file with key [%s] from Supabase Storage", key),
                            () -> client.getObjectInfo(authorization, bucket, key, null))
                        .map(info -> UploadResponse.of(key, SupabaseStorageCalls.eTag(info.body(), upload.body()), upload.body())));
            }));
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <E extends ObjectStorageEntry<?>> Publisher<Optional<E>> retrieve(@NonNull String key) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to retrieve a file with key [%s] from Supabase Storage", key),
                () -> client.getObjectInfo(authorization, configuration.getBucket(), key, null))
            .flatMap(info -> SupabaseStorageCalls.executeReactive(
                    String.format("Error when trying to download a file with key [%s] from Supabase Storage", key),
                    () -> client.getObject(authorization, configuration.getBucket(), key, null, null))
                .map(download -> {
                    byte[] content = download.body() != null ? download.body() : new byte[0];
                    String contentType = download.getHeaders().getContentType().orElse(null);
                    return Optional.of((E) new SupabaseStorageEntry(key, content, info.body(), contentType));
                }))
            .onErrorResume(SupabaseStorageException.class, e -> e.isNotFound() ? Mono.just(Optional.empty()) : Mono.error(e)));
    }

    /**
     * Deletes an object. Deleting a key that does not exist is not an error: the emitted list is then empty.
     *
     * @param key object path in the format {@code /foo/bar/file}
     * @return A publisher emitting the deleted objects as reported by Supabase Storage
     */
    @Override
    @NonNull
    public Publisher<List<ObjectSchema>> delete(@NonNull String key) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to delete a file with key [%s] from Supabase Storage", key),
                () -> client.deleteObjects(authorization, configuration.getBucket(), SupabaseStorageCalls.deleteRequest(key)))
            .map(response -> response.body() != null ? response.body() : Collections.<ObjectSchema>emptyList()));
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String key) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to check the existence of a file with key [%s] in Supabase Storage", key),
                () -> client.getObjectInfo(authorization, configuration.getBucket(), key, null))
            .map(response -> Boolean.TRUE)
            .onErrorResume(SupabaseStorageException.class, e -> e.isNotFound() ? Mono.just(Boolean.FALSE) : Mono.error(e)));
    }

    @Override
    @NonNull
    public Publisher<Set<String>> listObjects() {
        return Mono.from(listObjects(new ListObjectsRequest(SupabaseStorageCalls.DEFAULT_LIST_PAGE_SIZE)))
            .expand(page -> page.getContinuationToken()
                .map(token -> Mono.from(listObjects(new ListObjectsRequest(SupabaseStorageCalls.DEFAULT_LIST_PAGE_SIZE, null, token))))
                .orElseGet(Mono::empty))
            .flatMapIterable(ListObjectsResponse::getKeys)
            .collect(Collectors.<String, Set<String>>toCollection(LinkedHashSet::new));
    }

    /**
     * Lists a page of objects through the flat (no delimiter) variant of the Supabase Storage list API, ordered
     * by name. The continuation token wraps the Supabase cursor and is bound to the prefix and page size of the
     * request that produced it.
     *
     * @param request the paginated listing request
     * @return A publisher emitting the keys of the page and the continuation token for the next one
     */
    @Override
    @NonNull
    public Publisher<ListObjectsResponse> listObjects(@NonNull ListObjectsRequest request) {
        return withAuthorization(authorization -> {
            ListObjectsV2Request body = SupabaseStorageCalls.listRequest(request);
            return SupabaseStorageCalls.executeReactive(
                    String.format("Error when listing the objects of the bucket [%s] in Supabase Storage", configuration.getBucket()),
                    () -> client.listObjectsV2(authorization, configuration.getBucket(), body))
                .map(response -> SupabaseStorageCalls.listResponse(request, response.body()))
                .onErrorResume(SupabaseStorageException.class, e -> e.hasCode(SupabaseStorageException.NO_SUCH_BUCKET)
                    ? Mono.just(new ListObjectsResponse(Collections.emptyList()))
                    : Mono.error(e));
        });
    }

    /**
     * Copies an object within the configured bucket, including its user metadata. Supabase Storage refuses to
     * copy over an existing object, so an existing destination is deleted first; the two steps are not atomic.
     *
     * @param sourceKey the key of the source object
     * @param destinationKey the key of the destination object
     * @return A publisher that completes once the object is copied
     */
    @Override
    @NonNull
    public Publisher<Void> copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        return withAuthorization(authorization -> {
            String message = String.format("Error when trying to copy a file from key [%s] to key [%s] in Supabase Storage", sourceKey, destinationKey);
            CopyObjectRequest body = SupabaseStorageCalls.copyRequest(configuration.getBucket(), sourceKey, destinationKey);
            Mono<HttpResponse<CopyObjectResponse>> copy = SupabaseStorageCalls.executeReactive(message, () -> client.copyObject(authorization, body));
            return copy
                .onErrorResume(SupabaseStorageException.class, e -> e.hasCode(SupabaseStorageException.KEY_ALREADY_EXISTS)
                    ? Mono.from(delete(destinationKey)).then(copy)
                    : Mono.error(e))
                .then();
        });
    }

    /**
     * @return The {@code Authorization} header value sent with every request
     */
    @NonNull
    protected String getAuthorization() {
        return SupabaseStorageCalls.authorization(supabaseConfiguration);
    }

    /**
     * @param request the upload request
     * @param body the content of the upload request
     * @return A Supabase Storage upload request built from a Micronaut's {@link UploadRequest}
     */
    @NonNull
    protected SupabaseStorageUploadRequest getRequestBuilder(@NonNull UploadRequest request, byte @NonNull [] body) {
        return SupabaseStorageCalls.uploadRequest(configuration.getBucket(), request, body);
    }

    private <T> Mono<T> withAuthorization(Function<String, Mono<T>> call) {
        return Mono.defer(() -> call.apply(getAuthorization()));
    }

    private Mono<byte[]> body(UploadRequest request) {
        if (request instanceof BytesUploadRequest bytesUploadRequest) {
            return Mono.just(bytesUploadRequest.getBytes());
        }
        return Mono.fromCallable(() -> {
            try (InputStream inputStream = request.getInputStream()) {
                return inputStreamMapper.toByteArray(inputStream);
            } catch (IOException e) {
                throw new ObjectStorageException("Error uploading file", e);
            }
        }).subscribeOn(blockingScheduler);
    }

    private String encodeMetadata(Map<String, String> metadata) {
        return SupabaseStorageCalls.encodeMetadata(jsonMapper, metadata);
    }
}
