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
package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.auth.RegionProvider;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Oracle Cloud implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(OracleCloudStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = OracleCloudStorageConfiguration.class)
public class OracleCloudStorageOperations
    implements ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;
    private static final Logger LOG = LoggerFactory.getLogger(OracleCloudStorageOperations.class);

    private final OracleCloudStorageConfiguration configuration;
    private final ObjectStorage client;
    private final RegionProvider regionProvider;
    private final UploadManager uploadManager;

    /**
     * @param configuration Oracle Cloud Storage Configuration
     * @param client Object Storage Client
     * @param regionProvider Region provider, to determine the current region
     */
    public OracleCloudStorageOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                        ObjectStorage client, RegionProvider regionProvider) {
        this(configuration, client, regionProvider, () -> new UploadManager(client, UploadConfiguration.builder().build()));
    }

    OracleCloudStorageOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                 ObjectStorage client,
                                 RegionProvider regionProvider,
                                 Supplier<UploadManager> uploadManagerSupplier) {
        this.configuration = configuration;
        this.client = client;
        this.regionProvider = regionProvider;
        this.uploadManager = uploadManagerSupplier.get();
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest request) {
        return upload(request, builder -> { });
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest request,
                                    @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        try {
            PutObjectRequest putObjectRequest = builder.build();
            Long contentLength = putObjectRequest.getContentLength();
            PutObjectResponse response = Optional.ofNullable(contentLength)
                .map(length -> uploadWithManager(putObjectRequest, length))
                .orElseGet(() -> client.putObject(putObjectRequest));
            return UploadResponse.of(request.getKey(), response.getETag(), response);
        } catch (BmcException e) {
            throw new ObjectStorageException("Error when trying to upload an object to Oracle Cloud Storage", e);
        }
    }

    @NonNull
    private PutObjectResponse uploadWithManager(@NonNull PutObjectRequest putObjectRequest, long contentSize) {
        UploadManager.UploadResponse response = uploadManager.upload(
            UploadManager.UploadRequest.builder(putObjectRequest.getPutObjectBody(), contentSize)
                .build(putObjectRequest)
        );
        return PutObjectResponse.builder()
            .eTag(response.getETag())
            .opcClientRequestId(response.getOpcClientRequestId())
            .opcRequestId(response.getOpcRequestId())
            .opcContentMd5(response.getContentMd5())
            .opcContentCrc32c(response.getContentCrc32c())
            .opcContentSha256(response.getContentSha256())
            .opcContentSha384(response.getContentSha384())
            .build();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public Optional<OracleCloudStorageEntry> retrieve(@NonNull String key) {
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .objectName(key);

        try {
            GetObjectResponse objectResponse = client.getObject(builder.build());
            OracleCloudStorageEntry storageEntry = new OracleCloudStorageEntry(key, objectResponse);
            return Optional.of(storageEntry);
        } catch (BmcException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error when trying to retrieve an object from Oracle Cloud Storage: {}", e.getMessage(), e);
            }
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public DeleteObjectResponse delete(@NonNull String key) {
        try {
            return client.deleteObject(DeleteObjectRequest.builder()
                .bucketName(configuration.getBucket())
                .namespaceName(configuration.getNamespace())
                .objectName(key)
                .build());
        } catch (BmcException e) {
            throw new ObjectStorageException("Error when trying to delete an object from Oracle Cloud Storage", e);
        }
    }

    @Override
    public boolean exists(@NonNull String key) {
        try {
            client.headObject(HeadObjectRequest.builder()
                .bucketName(configuration.getBucket())
                .namespaceName(configuration.getNamespace())
                .objectName(key)
                .build());
            return true;
        } catch (BmcException e) {
            return false;
        }
    }

    @NonNull
    @Override
    public Set<String> listObjects() {
        Set<String> keys = new LinkedHashSet<>();
        String continuationToken = null;
        do {
            ListObjectsResponse response = listObjects(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken));
            keys.addAll(response.getKeys());
            continuationToken = response.getContinuationToken().orElse(null);
        } while (continuationToken != null);
        return keys;
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        String bucket = configuration.getBucket();
        try {
            com.oracle.bmc.objectstorage.responses.ListObjectsResponse response = client.listObjects(com.oracle.bmc.objectstorage.requests.ListObjectsRequest.builder()
                .bucketName(bucket)
                .namespaceName(configuration.getNamespace())
                .prefix(request.getPrefix().orElse(null))
                .limit(request.getPageSize())
                .startAfter(request.getContinuationToken().orElse(null))
                .build());

            List<String> keys = response.getListObjects()
                .getObjects()
                .stream()
                .map(ObjectSummary::getName)
                .toList();
            return new ListObjectsResponse(keys, response.getListObjects().getNextStartWith());
        } catch (BmcException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error when listing the objects in the bucket {} from Oracle Cloud Storage: {}", bucket, e.getMessage(), e);
            }
            return new ListObjectsResponse(Collections.emptyList());
        }
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        String bucket = configuration.getBucket();
        try {
            client.copyObject(CopyObjectRequest.builder()
                .bucketName(bucket)
                .namespaceName(configuration.getNamespace())
                .copyObjectDetails(CopyObjectDetails.builder()
                    .sourceObjectName(sourceKey)
                    .destinationObjectName(destinationKey)
                    .destinationBucket(bucket)
                    .destinationNamespace(configuration.getNamespace())
                    .destinationRegion(regionProvider.getRegion().getRegionId())
                    .build())
                .build());
        } catch (BmcException e) {
            String msg = String.format("Error when copying a file from the key [%s] to the key [%s] in the Oracle Cloud Storage bucket [%s]", sourceKey, destinationKey, bucket);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public Optional<PresignedUpload> createPresignedUpload(@NonNull CreatePresignedUploadRequest request) {
        try {
            Instant expiration = Instant.now().plus(request.getExpiresIn());
            var preauthenticatedRequest = client.createPreauthenticatedRequest(
                CreatePreauthenticatedRequestRequest.builder()
                    .namespaceName(configuration.getNamespace())
                    .bucketName(configuration.getBucket())
                    .createPreauthenticatedRequestDetails(CreatePreauthenticatedRequestDetails.builder()
                        .name(getPreauthenticatedRequestName(request))
                        .objectName(request.getKey())
                        .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite)
                        .timeExpires(Date.from(expiration))
                        .build())
                    .build())
                .getPreauthenticatedRequest();
            return Optional.of(new PresignedUpload(
                getPreauthenticatedRequestUri(preauthenticatedRequest),
                "PUT",
                toPortableHeaders(request),
                expiration
            ));
        } catch (RuntimeException e) {
            String msg = String.format(
                "Error when trying to create a pre-signed upload request with key [%s] in Oracle Cloud Storage",
                request.getKey()
            );
            throw new ObjectStorageException(msg, e);
        }
    }

    /**
     *
     * @param request Upload Request
     * @return The Put Object Request Builder
     */
    protected PutObjectRequest.@NonNull Builder getRequestBuilder(@NonNull UploadRequest request) {
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
            .objectName(request.getKey())
            .bucketName(configuration.getBucket())
            .namespaceName(configuration.getNamespace())
            .putObjectBody(request.getInputStream());

        request.getContentSize().ifPresent(putObjectRequestBuilder::contentLength);
        request.getContentType().ifPresent(putObjectRequestBuilder::contentType);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            putObjectRequestBuilder.opcMeta(request.getMetadata());
        }
        return putObjectRequestBuilder;
    }

    /**
     * @param request the pre-signed upload request
     * @return the Oracle Cloud pre-authenticated request name
     * @since 3.0.0
     */
    @NonNull
    protected String getPreauthenticatedRequestName(@NonNull CreatePresignedUploadRequest request) {
        return "micronaut-presigned-upload-" + request.getKey().replace('/', '-');
    }

    /**
     * @param preauthenticatedRequest the Oracle Cloud pre-authenticated request
     * @return the absolute request URI that clients should replay
     * @since 3.0.0
     */
    @NonNull
    protected URI getPreauthenticatedRequestUri(@NonNull PreauthenticatedRequest preauthenticatedRequest) {
        String path = Optional.ofNullable(preauthenticatedRequest.getAccessUri())
            .orElseGet(preauthenticatedRequest::getFullPath);
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Oracle Cloud pre-authenticated request did not include an access URI");
        }
        return URI.create(Objects.requireNonNull(client.getEndpoint(), "client endpoint")).resolve(path);
    }

    /**
     * @param request the pre-signed upload request
     * @return portable headers that callers should preserve on upload
     * @since 3.0.0
     */
    @NonNull
    protected Map<String, List<String>> toPortableHeaders(@NonNull CreatePresignedUploadRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getContentType().ifPresent(contentType -> headers.put("Content-Type", List.of(contentType)));
        request.getContentLength().ifPresent(contentLength -> headers.put("Content-Length", List.of(Long.toString(contentLength))));
        request.getMetadata().forEach((key, value) -> headers.put("opc-meta-" + key, List.of(value)));
        return headers;
    }
}
