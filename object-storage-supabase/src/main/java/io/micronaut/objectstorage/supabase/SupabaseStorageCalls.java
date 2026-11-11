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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.supabase.SupabaseConfiguration;
import io.micronaut.supabase.storage.CopyObjectRequest;
import io.micronaut.supabase.storage.CreateBucketRequest;
import io.micronaut.supabase.storage.DeleteObjectsRequest;
import io.micronaut.supabase.storage.ListObjectsV2Request;
import io.micronaut.supabase.storage.ListObjectsV2Response;
import io.micronaut.supabase.storage.ObjectInfoSchema;
import io.micronaut.supabase.storage.ObjectSchema;
import io.micronaut.supabase.storage.StorageErrorSchema;
import io.micronaut.supabase.storage.UploadObjectResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Shared plumbing for calling {@link io.micronaut.supabase.storage.SupabaseStorageClient} and
 * {@link io.micronaut.supabase.storage.ReactiveSupabaseStorageClient}: error mapping, credentials, and the
 * request and continuation token encoding the blocking and reactive operations have in common.
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
@Internal
final class SupabaseStorageCalls {

    /**
     * Page size used when listing every object of a bucket.
     */
    static final int DEFAULT_LIST_PAGE_SIZE = 1_000;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int FIRST_ERROR_STATUS = 400;
    private static final String CONTINUATION_TOKEN_PREFIX = "supabase:v1:";
    private static final String TOKEN_SEPARATOR = "\n";
    private static final int TOKEN_PARTS = 3;
    private static final String SORT_COLUMN = "name";
    private static final String SORT_ORDER = "asc";

    private SupabaseStorageCalls() {
    }

    /**
     * Executes a Supabase Storage request and turns every failure into an {@link ObjectStorageException}.
     *
     * <p>The declarative client throws {@link HttpClientResponseException} for most error statuses but returns
     * {@code 404} responses as they are, so both paths are handled.</p>
     *
     * @param message Description of the operation, used as the exception message
     * @param request The request to execute
     * @param <T> Response body type
     * @return The successful response
     * @throws SupabaseStorageException when the server rejects the request
     * @throws ObjectStorageException when the request cannot be sent
     */
    @NonNull
    static <T> HttpResponse<T> execute(@NonNull String message, @NonNull Supplier<HttpResponse<T>> request) {
        HttpResponse<T> response;
        try {
            response = request.get();
        } catch (HttpClientResponseException e) {
            throw failure(message, e.getResponse(), e);
        } catch (HttpClientException e) {
            throw new ObjectStorageException(message, e);
        }
        if (response.getStatus().getCode() >= FIRST_ERROR_STATUS) {
            throw failure(message, response, null);
        }
        return response;
    }

    /**
     * Executes a reactive Supabase Storage request and turns every failure into an {@link ObjectStorageException}
     * error signal.
     *
     * <p>Nothing is sent until the returned publisher is subscribed to, and each subscription sends the request
     * again. The reactive client fails its publisher with {@link HttpClientResponseException} for every error status,
     * {@code 404} included; error responses emitted as values are mapped too, for safety.</p>
     *
     * @param message Description of the operation, used as the exception message
     * @param request Supplies the publisher of the request to execute
     * @param <T> Response body type
     * @return A publisher that emits the successful response, or fails with {@link SupabaseStorageException} when
     * the server rejects the request and with {@link ObjectStorageException} when the request cannot be sent
     */
    @NonNull
    static <T> Mono<HttpResponse<T>> executeReactive(@NonNull String message,
                                                     @NonNull Supplier<Publisher<HttpResponse<T>>> request) {
        return Mono.defer(() -> Mono.from(request.get()))
            .onErrorMap(HttpClientResponseException.class, e -> failure(message, e.getResponse(), e))
            .onErrorMap(HttpClientException.class, e -> new ObjectStorageException(message, e))
            .flatMap(response -> response.getStatus().getCode() >= FIRST_ERROR_STATUS
                ? Mono.error(failure(message, response, null))
                : Mono.just(response));
    }

    /**
     * Resolves the bearer token for Supabase Storage requests from the Supabase project configuration: the
     * secret key when configured (service role, bypasses Row Level Security), otherwise the publishable key
     * (anonymous role, subject to Row Level Security), as the official Supabase clients do.
     *
     * @param configuration Supabase project configuration
     * @return The {@code Authorization} header value
     * @throws ObjectStorageException when no key is configured
     */
    @NonNull
    static String authorization(@NonNull SupabaseConfiguration configuration) {
        String key = StringUtils.isNotEmpty(configuration.getSecretKey())
            ? configuration.getSecretKey()
            : configuration.getPublishableKey();
        if (StringUtils.isEmpty(key)) {
            throw new ObjectStorageException("Supabase Storage needs supabase.secret-key or supabase.publishable-key to authenticate its requests");
        }
        return BEARER_PREFIX + key;
    }

    /**
     * @param bucket The bucket to upload to
     * @param request The portable upload request
     * @param body The object content
     * @return A Supabase Storage upload request carrying the key, content type and metadata of the portable one
     */
    @NonNull
    static SupabaseStorageUploadRequest uploadRequest(@NonNull String bucket, @NonNull UploadRequest request, byte @NonNull [] body) {
        SupabaseStorageUploadRequest supabaseRequest = new SupabaseStorageUploadRequest(bucket, request.getKey(), body);
        request.getContentType().ifPresent(supabaseRequest::setContentType);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            supabaseRequest.setMetadata(request.getMetadata());
        }
        return supabaseRequest;
    }

    /**
     * @param key The key to delete
     * @return The request deleting that single key
     */
    @NonNull
    static DeleteObjectsRequest deleteRequest(@NonNull String key) {
        return new DeleteObjectsRequest(List.of(key));
    }

    /**
     * @param bucket The bucket holding both objects
     * @param sourceKey The key of the source object
     * @param destinationKey The key of the destination object
     * @return The request copying the object and its user metadata within the bucket
     */
    @NonNull
    static CopyObjectRequest copyRequest(@NonNull String bucket, @NonNull String sourceKey, @NonNull String destinationKey) {
        return new CopyObjectRequest(bucket, sourceKey, null, bucket, destinationKey, null, true);
    }

    /**
     * @param name The bucket name
     * @return The request creating a private bucket without size or MIME type limits
     */
    @NonNull
    static CreateBucketRequest createBucketRequest(@NonNull String name) {
        return new CreateBucketRequest(name, null, false, null, null, null, null);
    }

    /**
     * Builds the flat (no delimiter) Supabase list request for a page, ordered by name, from the portable request
     * and its continuation token.
     *
     * @param request The paginated listing request
     * @return The Supabase Storage list request
     * @throws ObjectStorageException when the continuation token is malformed or belongs to another request
     */
    @NonNull
    static ListObjectsV2Request listRequest(@NonNull ListObjectsRequest request) {
        return new ListObjectsV2Request(
            request.getPrefix().orElse(null),
            null,
            request.getPageSize(),
            decodeContinuationToken(request),
            false,
            new io.micronaut.supabase.storage.ListObjectsRequest.SortBy(SORT_COLUMN, SORT_ORDER),
            null,
            null);
    }

    /**
     * @param request The paginated listing request that produced the response
     * @param response The Supabase Storage page
     * @return The keys of the page and the continuation token for the next one
     */
    @NonNull
    static ListObjectsResponse listResponse(@NonNull ListObjectsRequest request, @NonNull ListObjectsV2Response response) {
        List<String> keys = response.objects().stream().map(ObjectSchema::name).toList();
        return new ListObjectsResponse(keys, encodeContinuationToken(request, response, keys));
    }

    /**
     * Supabase Storage does not return an entity tag from the upload route, so it is read from the object
     * information, falling back to the version and object identifiers.
     *
     * @param info The information of the uploaded object
     * @param response The upload response
     * @return The entity tag of the uploaded object
     */
    @NonNull
    static String eTag(@NonNull ObjectInfoSchema info, @NonNull UploadObjectResponse response) {
        if (StringUtils.isNotEmpty(info.etag())) {
            return info.etag();
        }
        if (StringUtils.isNotEmpty(info.version())) {
            return info.version();
        }
        return response.id() != null ? response.id() : "";
    }

    /**
     * @param jsonMapper The JSON mapper
     * @param metadata The user metadata
     * @return The metadata as the Base64-encoded JSON the Supabase Storage metadata header carries, or
     * {@code null} when there is none
     */
    @Nullable
    static String encodeMetadata(@NonNull JsonMapper jsonMapper, @Nullable Map<String, String> metadata) {
        if (CollectionUtils.isEmpty(metadata)) {
            return null;
        }
        try {
            return Base64.getEncoder().encodeToString(jsonMapper.writeValueAsBytes(metadata));
        } catch (IOException e) {
            throw new ObjectStorageException("Error encoding the object metadata for Supabase Storage", e);
        }
    }

    private static SupabaseStorageException failure(String message, HttpResponse<?> response, @Nullable Throwable cause) {
        StorageErrorSchema error = response.getBody(Argument.of(StorageErrorSchema.class)).orElse(null);
        return new SupabaseStorageException(message, response.getStatus(), error, cause);
    }

    @Nullable
    private static String decodeContinuationToken(ListObjectsRequest request) {
        String continuationToken = request.getContinuationToken().orElse(null);
        if (continuationToken == null) {
            return null;
        }
        if (!continuationToken.startsWith(CONTINUATION_TOKEN_PREFIX)) {
            throw new ObjectStorageException("Invalid Supabase Storage continuation token");
        }
        try {
            String decodedToken = decodeTokenPart(continuationToken.substring(CONTINUATION_TOKEN_PREFIX.length()));
            String[] parts = decodedToken.split(TOKEN_SEPARATOR, -1);
            if (parts.length != TOKEN_PARTS) {
                throw new ObjectStorageException("Invalid Supabase Storage continuation token");
            }
            String prefix = decodeTokenPart(parts[0]);
            String pageSize = decodeTokenPart(parts[1]);
            if (!request.getPrefix().orElse("").equals(prefix) || !Integer.toString(request.getPageSize()).equals(pageSize)) {
                throw new ObjectStorageException("Supabase Storage continuation token does not match the current request");
            }
            return decodeTokenPart(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid Supabase Storage continuation token", e);
        }
    }

    @Nullable
    private static String encodeContinuationToken(ListObjectsRequest request, ListObjectsV2Response response, List<String> keys) {
        if (!Boolean.TRUE.equals(response.hasNext()) || StringUtils.isEmpty(response.nextCursor()) || keys.isEmpty()) {
            return null;
        }
        String payload = new StringJoiner(TOKEN_SEPARATOR)
            .add(encodeTokenPart(request.getPrefix().orElse("")))
            .add(encodeTokenPart(Integer.toString(request.getPageSize())))
            .add(encodeTokenPart(response.nextCursor()))
            .toString();
        return CONTINUATION_TOKEN_PREFIX + encodeTokenPart(payload);
    }

    private static String encodeTokenPart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeTokenPart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
