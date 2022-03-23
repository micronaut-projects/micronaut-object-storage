/*
 * Copyright 2021 original authors
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

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.UploadRequest;
import io.micronaut.objectstorage.UploadResponse;

import java.util.Optional;

/**
 * @author Pavol Gressa
 */
@EachBean(OracleCloudBucketConfiguration.class)
public class OracleCloudBucket implements io.micronaut.objectstorage.ObjectStorage {

    private final ObjectStorage client;
    private final OracleCloudBucketConfiguration oracleCloudBucketConfiguration;

    public OracleCloudBucket(@Parameter OracleCloudBucketConfiguration oracleCloudBucketConfiguration, ObjectStorage client) {
        this.client = client;
        this.oracleCloudBucketConfiguration = oracleCloudBucketConfiguration;
    }

    @Override
    public UploadResponse put(UploadRequest uploadRequest) throws ObjectStorageException {
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
            .objectName(uploadRequest.getKey())
            .bucketName(oracleCloudBucketConfiguration.getName())
            .namespaceName(oracleCloudBucketConfiguration.getNamespace())
            .putObjectBody(uploadRequest.getInputStream());

        uploadRequest.getContentSize().ifPresent(putObjectRequestBuilder::contentLength);
        uploadRequest.getContentType().ifPresent(putObjectRequestBuilder::contentType);

        PutObjectResponse putObjectResponse = client.putObject(putObjectRequestBuilder.build());
        return new UploadResponse.Builder().withETag(putObjectResponse.getETag()).build();
    }

    @Override
    public Optional<ObjectStorageEntry> get(String key) throws ObjectStorageException {
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
            .bucketName(oracleCloudBucketConfiguration.getName())
            .namespaceName(oracleCloudBucketConfiguration.getNamespace())
            .objectName(key);


        GetObjectResponse objectResponse = client.getObject(builder.build());
        OracleCloudObjectStorageEntry storageEntry = new OracleCloudObjectStorageEntry(key, objectResponse);
        return Optional.of(storageEntry);
    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder()
            .bucketName(oracleCloudBucketConfiguration.getName())
            .namespaceName(oracleCloudBucketConfiguration.getNamespace())
            .objectName(key);

        client.deleteObject(builder.build());
    }
}
