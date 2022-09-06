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

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Oracle Cloud implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(OracleCloudStorageConfiguration.class)
public class OracleCloudStorageOperations
    implements ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(OracleCloudStorageOperations.class);

    private final OracleCloudStorageConfiguration configuration;
    private final ObjectStorage client;

    /**
     *
     * @param configuration Oracle Cloud Storage Configuration
     * @param client Object Storage Client
     */
    public OracleCloudStorageOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                        ObjectStorage client) {
        this.client = client;
        this.configuration = configuration;
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
        return putObjectRequestBuilder;
    }
}
