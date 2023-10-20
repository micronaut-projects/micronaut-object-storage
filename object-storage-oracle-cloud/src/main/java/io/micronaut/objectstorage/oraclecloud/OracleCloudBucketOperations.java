/*
 * Copyright 2017-2023 original authors
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
import com.oracle.bmc.objectstorage.model.BucketSummary;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest;
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListBucketsResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OCI bucket operations.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@EachBean(OracleCloudNamespaceConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = OracleCloudNamespaceConfiguration.class)
final class OracleCloudBucketOperations
    implements BucketOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {
    private final OracleCloudNamespaceConfiguration configuration;
    private final ObjectStorage client;
    private final RegionProvider regionProvider;

    OracleCloudBucketOperations(OracleCloudNamespaceConfiguration configuration, ObjectStorage client, RegionProvider regionProvider) {
        this.configuration = configuration;
        this.client = client;
        this.regionProvider = regionProvider;
    }

    @Override
    public void createBucket(String name) {
        try {
            client.createBucket(CreateBucketRequest.builder()
                .namespaceName(configuration.getNamespace())
                .createBucketDetails(CreateBucketDetails.builder()
                    .compartmentId(configuration.getCompartmentId())
                    .name(name)
                    .build())
                .build());
        } catch (BmcException e) {
            String msg = String.format("Error creating bucket with name [%s] in namespace [%s] in compartment [%s]", name, configuration.getNamespace(), configuration.getCompartmentId());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void deleteBucket(String name) {
        try {
            client.deleteBucket(DeleteBucketRequest.builder()
                .namespaceName(configuration.getNamespace())
                .bucketName(name)
                .build());
        } catch (BmcException e) {
            String msg = String.format("Error deleting bucket with name [%s] in namespace [%s]", name, configuration.getNamespace());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public Set<String> listBuckets() {
        try {
            ListBucketsResponse response = client.listBuckets(ListBucketsRequest.builder()
                .compartmentId(configuration.getCompartmentId())
                .namespaceName(configuration.getNamespace())
                .build());
            return response.getItems().stream()
                .map(BucketSummary::getName)
                .collect(Collectors.toSet());
        } catch (BmcException e) {
            String msg = String.format("Error listing buckets in namespace [%s] in compartment [%s]", configuration.getNamespace(), configuration.getCompartmentId());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> storageForBucket(String bucket) {
        Objects.requireNonNull(bucket, "bucket");
        OracleCloudStorageConfiguration cfg = new OracleCloudStorageConfiguration("");
        cfg.setNamespace(configuration.getNamespace());
        cfg.setBucket(bucket);
        return new OracleCloudStorageOperations(cfg, client, regionProvider);
    }
}
