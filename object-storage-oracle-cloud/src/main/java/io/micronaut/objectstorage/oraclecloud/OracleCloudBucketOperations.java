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

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest;
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest;
import com.oracle.bmc.objectstorage.requests.GetBucketRequest;
import com.oracle.bmc.objectstorage.responses.GetBucketResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * OCI bucket operations.
 *
 * @since 3.1.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(OracleCloudStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = OracleCloudStorageConfiguration.class)
final class OracleCloudBucketOperations implements BucketOperations<GetBucketResponse> {
    private final OracleCloudStorageConfiguration configuration;
    private final ObjectStorage client;

    OracleCloudBucketOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                ObjectStorage client) {
        this.configuration = configuration;
        this.client = client;
    }

    @Override
    public void create(@NonNull String name) {
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
    public Optional<BucketEntry<GetBucketResponse>> retrieve(@NonNull String name) {
        try {
            GetBucketResponse response = client.getBucket(GetBucketRequest.builder()
                .namespaceName(configuration.getNamespace())
                .bucketName(name)
                .build());
            return Optional.of(new BucketEntry<>(name, response));
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            String msg = String.format("Error retrieving bucket with name [%s] in namespace [%s]", name, configuration.getNamespace());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void delete(@NonNull String name) {
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
}
