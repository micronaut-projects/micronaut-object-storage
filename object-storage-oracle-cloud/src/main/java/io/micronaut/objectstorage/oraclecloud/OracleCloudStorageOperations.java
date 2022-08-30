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
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.UploadRequest;
import io.micronaut.objectstorage.UploadResponse;

import java.util.Optional;

/**
 * Oracle Cloud implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(OracleCloudStorageConfiguration.class)
public class OracleCloudStorageOperations implements ObjectStorageOperations {

    private final ObjectStorage client;
    private final OracleCloudStorageConfiguration configuration;

    public OracleCloudStorageOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                        ObjectStorage client) {
        this.client = client;
        this.configuration = configuration;
    }

    @Override
    public UploadResponse upload(UploadRequest uploadRequest) throws ObjectStorageException {
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
            .objectName(uploadRequest.getKey())
            .bucketName(configuration.getName())
            .namespaceName(configuration.getNamespace())
            .putObjectBody(uploadRequest.getInputStream());

        uploadRequest.getContentSize().ifPresent(putObjectRequestBuilder::contentLength);
        uploadRequest.getContentType().ifPresent(putObjectRequestBuilder::contentType);

        PutObjectResponse putObjectResponse = client.putObject(putObjectRequestBuilder.build());

        return new UploadResponse.Builder()
            .withETag(putObjectResponse.getETag())
            .build();
    }

    @Override
    public Optional<ObjectStorageEntry> retrieve(String key) throws ObjectStorageException {
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
            .bucketName(configuration.getName())
            .namespaceName(configuration.getNamespace())
            .objectName(key);

        try {
            GetObjectResponse objectResponse = client.getObject(builder.build());
            OracleCloudStorageEntry storageEntry = new OracleCloudStorageEntry(key, objectResponse);
            return Optional.of(storageEntry);
        } catch (BmcException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        client.deleteObject(DeleteObjectRequest.builder()
            .bucketName(configuration.getName())
            .namespaceName(configuration.getNamespace())
            .objectName(key)
            .build());
    }
}
