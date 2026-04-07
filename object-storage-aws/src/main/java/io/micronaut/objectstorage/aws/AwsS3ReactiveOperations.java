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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.FileUploadRequest;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reactive AWS S3 object storage operations backed by the native async SDK client.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(AwsS3Configuration.class)
@Requires(beans = AwsS3Configuration.class)
@Requires(beans = S3AsyncClient.class)
public final class AwsS3ReactiveOperations implements ReactiveObjectStorageOperations<
    PutObjectRequest.Builder,
    PutObjectResponse,
    DeleteObjectResponse> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;
    private static final String LEGACY_CONTINUATION_TOKEN_PREFIX = "aws-s3:v1:";
    private static final String RAW_CONTINUATION_TOKEN_PREFIX = "aws-s3:v2:";
    private static final String CONTINUATION_TOKEN_PREFIX = "aws-s3:v3:";

    private final AwsS3Configuration configuration;
    private final S3AsyncClient s3AsyncClient;
    private final ExecutorService blockingExecutor;

    public AwsS3ReactiveOperations(@Parameter AwsS3Configuration configuration,
                                   S3AsyncClient s3AsyncClient,
                                   @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        this.configuration = configuration;
        this.s3AsyncClient = s3AsyncClient;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<PutObjectResponse>> upload(@NonNull UploadRequest request) {
        PutObjectRequest objectRequest = getRequestBuilder(request).build();
        return fromCompletableFuture(() -> s3AsyncClient
            .putObject(objectRequest, getRequestBody(request))
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw uploadException(request.getKey(), throwable);
                }
                return UploadResponse.of(request.getKey(), response.eTag(), response);
            }));
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<PutObjectResponse>> upload(@NonNull UploadRequest request,
                                                               @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        return fromCompletableFuture(() -> s3AsyncClient
            .putObject(builder.build(), getRequestBody(request))
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw uploadException(request.getKey(), throwable);
                }
                return UploadResponse.of(request.getKey(), response.eTag(), response);
            }));
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <E extends ObjectStorageEntry<?>> Publisher<Optional<E>> retrieve(@NonNull String key) {
        return fromCompletableFuture(() -> s3AsyncClient
            .getObject(
                GetObjectRequest.builder()
                    .bucket(configuration.getBucket())
                    .key(key)
                    .build(),
                AsyncResponseTransformer.toBlockingInputStream()
            )
            .handle((responseInputStream, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (isNoSuchKey(cause)) {
                        return Optional.<E>empty();
                    }
                    throw retrievalException(key, cause);
                }
                AwsS3ObjectStorageEntry entry = new AwsS3ObjectStorageEntry(key, responseInputStream);
                return Optional.of((E) entry);
            }));
    }

    @Override
    @NonNull
    public Publisher<DeleteObjectResponse> delete(@NonNull String key) {
        return fromCompletableFuture(() -> s3AsyncClient
            .deleteObject(DeleteObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build())
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
        return fromCompletableFuture(() -> s3AsyncClient
            .headObject(HeadObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build())
            .handle((response, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (isNoSuchKey(cause)) {
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
        return fromCompletableFuture(() -> {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            return collectKeys(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE), keys)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        if (isNoSuchBucket(cause)) {
                            return Collections.<String>emptySet();
                        }
                        throw listException(cause);
                    }
                    return result;
                });
        });
    }

    @Override
    @NonNull
    public Publisher<ListObjectsResponse> listObjects(@NonNull ListObjectsRequest request) {
        return fromCompletableFuture(() -> {
            DecodedContinuationToken decodedToken;
            try {
                decodedToken = decodeContinuationToken(request);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(configuration.getBucket())
                .maxKeys(request.getPageSize());
            request.getPrefix().ifPresent(builder::prefix);
            decodedToken.rawContinuationToken().ifPresent(builder::continuationToken);
            decodedToken.startAfter().ifPresent(builder::startAfter);
            return s3AsyncClient.listObjectsV2(builder.build())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        if (isNoSuchBucket(cause)) {
                            return new ListObjectsResponse(Collections.emptyList());
                        }
                        throw listException(cause);
                    }
                    List<String> keys = response.contents().stream().map(S3Object::key).toList();
                    return new ListObjectsResponse(keys, encodeContinuationToken(request, response, keys));
                });
        });
    }

    @Override
    @NonNull
    public Publisher<Void> copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        return Publishers.onComplete(
            Publishers.empty(),
            () -> s3AsyncClient.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(configuration.getBucket())
                    .destinationBucket(configuration.getBucket())
                    .sourceKey(sourceKey)
                    .destinationKey(destinationKey)
                    .build())
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
            .bucket(configuration.getBucket())
            .key(request.getKey());

        request.getContentType().ifPresent(builder::contentType);
        request.getContentSize().ifPresent(builder::contentLength);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            builder.metadata(request.getMetadata());
        }
        return builder;
    }

    private @NonNull AsyncRequestBody getRequestBody(@NonNull UploadRequest uploadRequest) {
        if (uploadRequest instanceof FileUploadRequest request) {
            return AsyncRequestBody.fromFile(request.getFile());
        }
        if (uploadRequest instanceof BytesUploadRequest request) {
            return AsyncRequestBody.fromBytes(request.getBytes());
        }
        return AsyncRequestBody.fromInputStream(
            uploadRequest.getInputStream(),
            uploadRequest.getContentSize().orElse(null),
            blockingExecutor
        );
    }

    private <T> Publisher<T> fromCompletableFuture(Supplier<CompletableFuture<T>> supplier) {
        return Publishers.fromCompletableFuture(() -> CompletableFuture
            .supplyAsync(supplier, blockingExecutor)
            .thenCompose(Function.identity()));
    }

    private CompletableFuture<Set<String>> collectKeys(ListObjectsRequest request, LinkedHashSet<String> keys) {
        DecodedContinuationToken decodedToken = decodeContinuationToken(request);
        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
            .bucket(configuration.getBucket())
            .maxKeys(request.getPageSize());
        request.getPrefix().ifPresent(builder::prefix);
        decodedToken.rawContinuationToken().ifPresent(builder::continuationToken);
        decodedToken.startAfter().ifPresent(builder::startAfter);
        return s3AsyncClient.listObjectsV2(builder.build())
            .thenCompose(response -> {
                List<String> pageKeys = response.contents().stream().map(S3Object::key).toList();
                keys.addAll(pageKeys);
                String continuationToken = encodeContinuationToken(request, response, pageKeys);
                if (continuationToken == null) {
                    return CompletableFuture.completedFuture(keys);
                }
                return collectKeys(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken), keys);
            });
    }

    private RuntimeException uploadException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to upload a file with key [%s] to Amazon S3", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException retrievalException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to retrieve a file with key [%s] from Amazon S3", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException deleteException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to delete a file with key [%s] from Amazon S3", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException existsException(String key, Throwable throwable) {
        String msg = String.format("Error when trying to check the existence of a file with key [%s] in Amazon S3", key);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException listException(Throwable throwable) {
        String msg = String.format("Error when listing the objects of the bucket [%s] in Amazon S3", configuration.getBucket());
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException copyException(String sourceKey, String destinationKey, Throwable throwable) {
        String msg = String.format("Error when trying to copy a file from key [%s] to key [%s] in Amazon S3", sourceKey, destinationKey);
        return toObjectStorageException(msg, throwable);
    }

    private RuntimeException toObjectStorageException(String message, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ObjectStorageException exception) {
            return exception;
        }
        if (cause instanceof AwsServiceException || cause instanceof SdkClientException) {
            return new ObjectStorageException(message, cause);
        }
        if (cause instanceof RuntimeException exception) {
            return exception;
        }
        return new ObjectStorageException(message, cause);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isNoSuchKey(Throwable throwable) {
        return throwable instanceof NoSuchKeyException
            || (throwable instanceof AwsServiceException exception
                && (exception.statusCode() == 404 || "NoSuchKey".equals(exception.awsErrorDetails().errorCode())));
    }

    private boolean isNoSuchBucket(Throwable throwable) {
        return throwable instanceof NoSuchBucketException
            || (throwable instanceof AwsServiceException exception
                && (exception.statusCode() == 404 || "NoSuchBucket".equals(exception.awsErrorDetails().errorCode())));
    }

    private DecodedContinuationToken decodeContinuationToken(ListObjectsRequest request) {
        String continuationToken = request.getContinuationToken().orElse(null);
        if (continuationToken == null || continuationToken.isEmpty()) {
            return new DecodedContinuationToken(Optional.empty(), Optional.empty());
        }
        try {
            if (!continuationToken.startsWith(CONTINUATION_TOKEN_PREFIX)) {
                if (continuationToken.startsWith(LEGACY_CONTINUATION_TOKEN_PREFIX)) {
                    String encodedToken = continuationToken.substring(LEGACY_CONTINUATION_TOKEN_PREFIX.length());
                    return new DecodedContinuationToken(Optional.of(new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8)), Optional.empty());
                }
                if (continuationToken.startsWith(RAW_CONTINUATION_TOKEN_PREFIX)) {
                    String encodedToken = continuationToken.substring(RAW_CONTINUATION_TOKEN_PREFIX.length());
                    return new DecodedContinuationToken(Optional.of(new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8)), Optional.empty());
                }
                return new DecodedContinuationToken(Optional.of(continuationToken), Optional.empty());
            }
            String encodedToken = continuationToken.substring(CONTINUATION_TOKEN_PREFIX.length());
            String decodedToken = new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8);
            String[] parts = decodedToken.split("\n", -1);
            if (parts.length != 3) {
                throw new ObjectStorageException("Invalid AWS S3 continuation token");
            }
            String expectedPrefix = request.getPrefix().orElse("");
            String expectedPageSize = Integer.toString(request.getPageSize());
            String prefix = decodeTokenPart(parts[0]);
            String pageSize = decodeTokenPart(parts[1]);
            String lastKey = decodeTokenPart(parts[2]);
            if (!expectedPrefix.equals(prefix) || !expectedPageSize.equals(pageSize)) {
                throw new ObjectStorageException("AWS S3 continuation token does not match the current request");
            }
            return new DecodedContinuationToken(Optional.empty(), Optional.of(lastKey));
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid AWS S3 continuation token", e);
        }
    }

    private String encodeContinuationToken(ListObjectsRequest request,
                                           ListObjectsV2Response response,
                                           List<String> keys) {
        if (response.nextContinuationToken() == null || response.nextContinuationToken().isEmpty()) {
            return null;
        }
        if (keys.isEmpty()) {
            return null;
        }
        String payload = new StringJoiner("\n")
            .add(encodeTokenPart(request.getPrefix().orElse("")))
            .add(encodeTokenPart(Integer.toString(request.getPageSize())))
            .add(encodeTokenPart(keys.get(keys.size() - 1)))
            .toString();
        return CONTINUATION_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeTokenPart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeTokenPart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private record DecodedContinuationToken(Optional<String> rawContinuationToken,
                                            Optional<String> startAfter) {
    }
}
