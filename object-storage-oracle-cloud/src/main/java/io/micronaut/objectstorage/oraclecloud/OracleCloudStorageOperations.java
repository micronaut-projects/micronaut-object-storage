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
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.ListObjects;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private static final Logger LOG = LoggerFactory.getLogger(OracleCloudStorageOperations.class);

    private final OracleCloudStorageConfiguration configuration;
    private final ObjectStorage client;
    private final RegionProvider regionProvider;

    /**
     * @param configuration Oracle Cloud Storage Configuration
     * @param client Object Storage Client
     * @deprecated use {@link #OracleCloudStorageOperations(OracleCloudStorageConfiguration, ObjectStorage, RegionProvider)}
     */
    @Deprecated
    public OracleCloudStorageOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                        ObjectStorage client) {
        this(configuration, client, null);
    }

    /**
     * @param configuration Oracle Cloud Storage Configuration
     * @param client Object Storage Client
     * @param regionProvider Region provider, to determine the current region
     */
    @Inject
    public OracleCloudStorageOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                        ObjectStorage client, RegionProvider regionProvider) {
        this.configuration = configuration;
        this.client = client;
        this.regionProvider = regionProvider;
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest request) {
        try {
            PutObjectResponse response = client.putObject(getRequestBuilder(request).build());
            return UploadResponse.of(request.getKey(), response.getETag(), response);
        } catch (BmcException e) {
            throw new ObjectStorageException("Error when trying to upload an object to Oracle Cloud Storage", e);
        }
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest request,
                                    @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        try {
            PutObjectResponse response = client.putObject(builder.build());
            return UploadResponse.of(request.getKey(), response.getETag(), response);
        } catch (BmcException e) {
            throw new ObjectStorageException("Error when trying to upload an object to Oracle Cloud Storage", e);
        }
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
        String bucket = configuration.getBucket();
        try {
            ListObjectsResponse response = client.listObjects(ListObjectsRequest.builder()
                .bucketName(bucket)
                .namespaceName(configuration.getNamespace())
                .build());

            return OracleCloudBucketOperations.paginate(
                    p -> {
                        ListObjectsRequest.Builder builder = ListObjectsRequest.builder()
                            .bucketName(bucket)
                            .namespaceName(configuration.getNamespace());
                        if (p != null) {
                            builder.start(p);
                        }
                        return client.listObjects(builder.build()).getListObjects();
                    },
                    ListObjects::getNextStartWith
                ).flatMap(lo -> lo.getObjects().stream())
                .map(ObjectSummary::getName)
                .collect(Collectors.toSet());
        } catch (BmcException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error when listing the objects in the bucket {} from Oracle Cloud Storage: {}", bucket, e.getMessage(), e);
            }
            return Collections.emptySet();
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

    /**
     *
     * @param request Upload Request
     * @return The Put Object Request Builder
     */
    @NonNull
    protected PutObjectRequest.Builder getRequestBuilder(@NonNull UploadRequest request) {
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
}
