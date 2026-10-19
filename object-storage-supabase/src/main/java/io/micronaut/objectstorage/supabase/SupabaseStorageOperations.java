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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.json.JsonMapper;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.PresignedUpload;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.supabase.SupabaseApiKeyClientFilter;
import io.micronaut.supabase.SupabaseConfiguration;
import io.micronaut.supabase.storage.ListObjectsV2Request;
import io.micronaut.supabase.storage.ListObjectsV2Response;
import io.micronaut.supabase.storage.ObjectInfoSchema;
import io.micronaut.supabase.storage.CopyObjectRequest;
import io.micronaut.supabase.storage.ObjectSchema;
import io.micronaut.supabase.storage.SignedUploadUrlResponse;
import io.micronaut.supabase.storage.SupabaseStorageClient;
import io.micronaut.supabase.storage.UploadObjectResponse;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Supabase Storage implementation of {@link ObjectStorageOperations}, built on the Micronaut Supabase
 * {@link SupabaseStorageClient}.
 *
 * <p>Requests authenticate with the bearer token returned by {@link #getAuthorization()}: by default the
 * {@code supabase.secret-key} (service role, bypasses Row Level Security) or, when absent, the
 * {@code supabase.publishable-key}. Override that method to use another token, such as the access token of
 * the current user.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
@EachBean(SupabaseStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = SupabaseStorageConfiguration.class)
public class SupabaseStorageOperations implements ObjectStorageOperations<SupabaseStorageUploadRequest, UploadObjectResponse, List<ObjectSchema>> {

    private static final String JWT_EXPIRATION_CLAIM = "exp";
    private static final int JWT_PARTS = 3;
    private static final char PATH_SEPARATOR = '/';

    private final SupabaseStorageConfiguration configuration;
    private final SupabaseStorageClient client;
    private final SupabaseConfiguration supabaseConfiguration;
    private final InputStreamMapper inputStreamMapper;
    private final JsonMapper jsonMapper;
    @Nullable
    private final ServiceHttpClientConfiguration serviceConfiguration;

    /**
     * @param configuration Object storage configuration
     * @param client Supabase Storage API client
     * @param supabaseConfiguration Supabase project configuration
     * @param inputStreamMapper InputStream Mapper
     * @param jsonMapper JSON mapper, used to encode user metadata
     * @param serviceConfiguration Configuration of the {@code supabase} HTTP service, when its URL is configured
     * explicitly under {@code micronaut.http.services.supabase}
     */
    public SupabaseStorageOperations(@Parameter SupabaseStorageConfiguration configuration,
                                     SupabaseStorageClient client,
                                     SupabaseConfiguration supabaseConfiguration,
                                     InputStreamMapper inputStreamMapper,
                                     JsonMapper jsonMapper,
                                     @Nullable @Named(SupabaseApiKeyClientFilter.SERVICE_ID) ServiceHttpClientConfiguration serviceConfiguration) {
        this.configuration = configuration;
        this.client = client;
        this.supabaseConfiguration = supabaseConfiguration;
        this.inputStreamMapper = inputStreamMapper;
        this.jsonMapper = jsonMapper;
        this.serviceConfiguration = serviceConfiguration;
    }

    @Override
    @NonNull
    public UploadResponse<UploadObjectResponse> upload(@NonNull UploadRequest request) {
        return upload(request, supabaseRequest -> { });
    }

    @Override
    @NonNull
    public UploadResponse<UploadObjectResponse> upload(@NonNull UploadRequest request,
                                                       @NonNull Consumer<SupabaseStorageUploadRequest> requestConsumer) {
        SupabaseStorageUploadRequest supabaseRequest = getRequestBuilder(request);
        requestConsumer.accept(supabaseRequest);
        String key = supabaseRequest.getObjectKey();
        String bucket = supabaseRequest.getBucketName();
        String authorization = getAuthorization();
        UploadObjectResponse response = SupabaseStorageCalls.execute(
            String.format("Error when trying to upload a file with key [%s] to Supabase Storage", key),
            () -> client.uploadObject(authorization, bucket, key, supabaseRequest.getContentType(),
                supabaseRequest.getCacheControl(), supabaseRequest.isUpsert(),
                encodeMetadata(supabaseRequest.getMetadata()), supabaseRequest.getBody())).body();
        return UploadResponse.of(key, eTag(bucket, key, response), response);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<SupabaseStorageEntry> retrieve(@NonNull String key) {
        String authorization = getAuthorization();
        try {
            ObjectInfoSchema info = SupabaseStorageCalls.execute(
                String.format("Error when trying to retrieve a file with key [%s] from Supabase Storage", key),
                () -> client.getObjectInfo(authorization, configuration.getBucket(), key, null)).body();
            HttpResponse<byte[]> download = SupabaseStorageCalls.execute(
                String.format("Error when trying to download a file with key [%s] from Supabase Storage", key),
                () -> client.getObject(authorization, configuration.getBucket(), key, null, null));
            byte[] content = download.body() != null ? download.body() : new byte[0];
            String contentType = download.getHeaders().getContentType().orElse(null);
            return Optional.of(new SupabaseStorageEntry(key, content, info, contentType));
        } catch (SupabaseStorageException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Deletes an object. Deleting a key that does not exist is not an error: the returned list is then empty.
     *
     * @param key object path in the format {@code /foo/bar/file}
     * @return The deleted objects as reported by Supabase Storage
     */
    @Override
    @NonNull
    public List<ObjectSchema> delete(@NonNull String key) {
        String authorization = getAuthorization();
        List<ObjectSchema> deleted = SupabaseStorageCalls.execute(
            String.format("Error when trying to delete a file with key [%s] from Supabase Storage", key),
            () -> client.deleteObjects(authorization, configuration.getBucket(), SupabaseStorageCalls.deleteRequest(key))).body();
        return deleted != null ? deleted : Collections.emptyList();
    }

    @Override
    public boolean exists(@NonNull String key) {
        String authorization = getAuthorization();
        try {
            SupabaseStorageCalls.execute(
                String.format("Error when trying to check the existence of a file with key [%s] in Supabase Storage", key),
                () -> client.getObjectInfo(authorization, configuration.getBucket(), key, null));
            return true;
        } catch (SupabaseStorageException e) {
            if (e.isNotFound()) {
                return false;
            }
            throw e;
        }
    }

    @Override
    @NonNull
    public Set<String> listObjects() {
        Set<String> keys = new LinkedHashSet<>();
        ListObjectsRequest request = new ListObjectsRequest(SupabaseStorageCalls.DEFAULT_LIST_PAGE_SIZE);
        String continuationToken;
        do {
            ListObjectsResponse response = listObjects(request);
            keys.addAll(response.getKeys());
            continuationToken = response.getContinuationToken().orElse(null);
            request = new ListObjectsRequest(SupabaseStorageCalls.DEFAULT_LIST_PAGE_SIZE, null, continuationToken);
        } while (continuationToken != null);
        return keys;
    }

    /**
     * Lists a page of objects through the flat (no delimiter) variant of the Supabase Storage list API, ordered
     * by name. The continuation token wraps the Supabase cursor and is bound to the prefix and page size of the
     * request that produced it.
     *
     * @param request the paginated listing request
     * @return the keys of the page and the continuation token for the next one
     */
    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        ListObjectsV2Request body = SupabaseStorageCalls.listRequest(request);
        String authorization = getAuthorization();
        ListObjectsV2Response response;
        try {
            response = SupabaseStorageCalls.execute(
                String.format("Error when listing the objects of the bucket [%s] in Supabase Storage", configuration.getBucket()),
                () -> client.listObjectsV2(authorization, configuration.getBucket(), body)).body();
        } catch (SupabaseStorageException e) {
            if (e.hasCode(SupabaseStorageException.NO_SUCH_BUCKET)) {
                return new ListObjectsResponse(Collections.emptyList());
            }
            throw e;
        }
        return SupabaseStorageCalls.listResponse(request, response);
    }

    /**
     * Copies an object within the configured bucket, including its user metadata. Supabase Storage refuses to
     * copy over an existing object, so an existing destination is deleted first; the two steps are not atomic.
     *
     * @param sourceKey the key of the source object
     * @param destinationKey the key of the destination object
     */
    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        String authorization = getAuthorization();
        String message = String.format("Error when trying to copy a file from key [%s] to key [%s] in Supabase Storage", sourceKey, destinationKey);
        CopyObjectRequest body = SupabaseStorageCalls.copyRequest(configuration.getBucket(), sourceKey, destinationKey);
        try {
            SupabaseStorageCalls.execute(message, () -> client.copyObject(authorization, body));
        } catch (SupabaseStorageException e) {
            if (!e.hasCode(SupabaseStorageException.KEY_ALREADY_EXISTS)) {
                throw e;
            }
            delete(destinationKey);
            SupabaseStorageCalls.execute(message, () -> client.copyObject(authorization, body));
        }
    }

    /**
     * Creates a signed upload URL for the given key.
     *
     * <p>Supabase Storage decides the validity of signed upload URLs ({@code UPLOAD_SIGNED_URL_EXPIRATION_TIME} on
     * the server, two hours by default); the requested duration is not sent, and the returned expiration is read
     * from the signed token. The signed URL replaces an existing object with the same key. The absolute URL needs
     * the Supabase project URL, from {@code micronaut.http.services.supabase.url} or the {@code supabase.*}
     * configuration; without it, an empty optional is returned.</p>
     *
     * @param request the pre-signed upload request parameters
     * @return the signed upload request
     */
    @Override
    @NonNull
    public Optional<PresignedUpload> createPresignedUpload(@NonNull CreatePresignedUploadRequest request) {
        Optional<String> storageUrl = getStorageUrl();
        if (storageUrl.isEmpty()) {
            return Optional.empty();
        }
        String authorization = getAuthorization();
        SignedUploadUrlResponse signed = SupabaseStorageCalls.execute(
            String.format("Error when trying to create a pre-signed upload request with key [%s] in Supabase Storage", request.getKey()),
            () -> client.signUploadUrl(authorization, configuration.getBucket(), request.getKey(), true)).body();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getContentType().ifPresent(contentType -> headers.put(HttpHeaders.CONTENT_TYPE, List.of(contentType)));
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            headers.put(SupabaseStorageClient.METADATA_HEADER, List.of(encodeMetadata(request.getMetadata())));
        }
        Instant expiration = tokenExpiration(signed.token())
            .orElseGet(() -> Instant.now().plus(request.getExpiresIn()));
        return Optional.of(new PresignedUpload(absoluteUri(storageUrl.get(), signed.url()), HttpMethod.PUT.name(), headers, expiration));
    }

    /**
     * @return The {@code Authorization} header value sent with every request
     */
    @NonNull
    protected String getAuthorization() {
        return SupabaseStorageCalls.authorization(supabaseConfiguration);
    }

    /**
     * @return The absolute URL of the Supabase Storage API, used to build signed URLs
     */
    @NonNull
    protected Optional<String> getStorageUrl() {
        if (serviceConfiguration != null && !serviceConfiguration.getUrls().isEmpty()) {
            String url = serviceConfiguration.getUrls().get(0).toString();
            String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            return Optional.of(base + supabaseConfiguration.getStoragePath());
        }
        return supabaseConfiguration.getStorageUrl();
    }

    /**
     * @param request the upload request
     * @return A Supabase Storage upload request built from a Micronaut's {@link UploadRequest}
     */
    @NonNull
    protected SupabaseStorageUploadRequest getRequestBuilder(@NonNull UploadRequest request) {
        return SupabaseStorageCalls.uploadRequest(configuration.getBucket(), request, getBody(request));
    }

    private byte[] getBody(UploadRequest request) {
        if (request instanceof BytesUploadRequest bytesUploadRequest) {
            return bytesUploadRequest.getBytes();
        }
        try (InputStream inputStream = request.getInputStream()) {
            return inputStreamMapper.toByteArray(inputStream);
        } catch (IOException e) {
            throw new ObjectStorageException("Error uploading file", e);
        }
    }

    /**
     * Supabase Storage does not return an entity tag from the upload route, so it is read from the object
     * information, falling back to the version and object identifiers.
     */
    private String eTag(String bucket, String key, UploadObjectResponse response) {
        String authorization = getAuthorization();
        ObjectInfoSchema info = SupabaseStorageCalls.execute(
            String.format("Error when trying to read the information of the uploaded file with key [%s] from Supabase Storage", key),
            () -> client.getObjectInfo(authorization, bucket, key, null)).body();
        return SupabaseStorageCalls.eTag(info, response);
    }

    @Nullable
    private String encodeMetadata(Map<String, String> metadata) {
        return SupabaseStorageCalls.encodeMetadata(jsonMapper, metadata);
    }

    private Optional<Instant> tokenExpiration(@Nullable String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != JWT_PARTS) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = jsonMapper.readValue(payload, Argument.mapOf(String.class, Object.class));
            if (claims.get(JWT_EXPIRATION_CLAIM) instanceof Number expiration) {
                return Optional.of(Instant.ofEpochSecond(expiration.longValue()));
            }
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static URI absoluteUri(String storageUrl, String signedUrl) {
        if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
            return URI.create(signedUrl);
        }
        String path = signedUrl.charAt(0) == PATH_SEPARATOR ? signedUrl : PATH_SEPARATOR + signedUrl;
        return URI.create(storageUrl + path);
    }
}
