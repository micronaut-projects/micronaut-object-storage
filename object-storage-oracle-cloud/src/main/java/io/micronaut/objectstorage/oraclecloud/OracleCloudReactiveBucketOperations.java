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
package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageAsync;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest;
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest;
import com.oracle.bmc.objectstorage.requests.GetBucketRequest;
import com.oracle.bmc.objectstorage.responses.CreateBucketResponse;
import com.oracle.bmc.objectstorage.responses.DeleteBucketResponse;
import com.oracle.bmc.objectstorage.responses.GetBucketResponse;
import com.oracle.bmc.responses.AsyncHandler;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Reactive OCI bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(OracleCloudStorageConfiguration.class)
@Requires(beans = OracleCloudStorageConfiguration.class)
@Requires(beans = ObjectStorageAsync.class)
public class OracleCloudReactiveBucketOperations implements ReactiveBucketOperations<GetBucketResponse> {

    private final OracleCloudStorageConfiguration configuration;
    private final ObjectStorageAsync client;

    public OracleCloudReactiveBucketOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                               ObjectStorageAsync client) {
        this.configuration = configuration;
        this.client = client;
    }

    @Override
    @NonNull
    public Publisher<Void> create(@NonNull String name) {
        CreateBucketDetails.Builder detailsBuilder = CreateBucketDetails.builder()
            .name(name);
        if (configuration.getCompartmentId() != null) {
            detailsBuilder.compartmentId(configuration.getCompartmentId());
        }
        CreateBucketRequest request = CreateBucketRequest.builder()
            .namespaceName(configuration.getNamespace())
            .createBucketDetails(detailsBuilder.build())
            .build();
        return Publishers.fromCompletableFuture(() -> this.<CreateBucketRequest, CreateBucketResponse, Void>invoke(
            handler -> client.createBucket(request, handler),
            ignored -> null,
            throwable -> {
                String msg = String.format("Error creating bucket with name [%s] in namespace [%s] in compartment [%s]", name, configuration.getNamespace(), configuration.getCompartmentId());
                return new ObjectStorageException(msg, throwable);
            }
        ));
    }

    @Override
    @NonNull
    public Publisher<Optional<BucketEntry<GetBucketResponse>>> retrieve(@NonNull String name) {
        GetBucketRequest request = GetBucketRequest.builder()
            .namespaceName(configuration.getNamespace())
            .bucketName(name)
            .build();
        return Publishers.fromCompletableFuture(() -> {
            CompletableFuture<Optional<BucketEntry<GetBucketResponse>>> future = new CompletableFuture<>();
            client.getBucket(request, new AsyncHandler<>() {
                @Override
                public void onSuccess(GetBucketRequest ignored, GetBucketResponse response) {
                    future.complete(Optional.of(new BucketEntry<>(name, response)));
                }

                @Override
                public void onError(GetBucketRequest ignored, Throwable error) {
                    Throwable cause = unwrap(error);
                    if (cause instanceof BmcException bmcException && bmcException.getStatusCode() == 404) {
                        future.complete(Optional.empty());
                        return;
                    }
                    String msg = String.format("Error retrieving bucket with name [%s] in namespace [%s]", name, configuration.getNamespace());
                    future.completeExceptionally(new ObjectStorageException(msg, cause));
                }
            });
            return future;
        });
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String name) {
        DeleteBucketRequest request = DeleteBucketRequest.builder()
            .namespaceName(configuration.getNamespace())
            .bucketName(name)
            .build();
        return Publishers.fromCompletableFuture(() -> this.<DeleteBucketRequest, DeleteBucketResponse, Void>invoke(
            handler -> client.deleteBucket(request, handler),
            ignored -> null,
            throwable -> {
                String msg = String.format("Error deleting bucket with name [%s] in namespace [%s]", name, configuration.getNamespace());
                return new ObjectStorageException(msg, throwable);
            }
        ));
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String name) {
        GetBucketRequest request = GetBucketRequest.builder()
            .namespaceName(configuration.getNamespace())
            .bucketName(name)
            .build();
        return Publishers.fromCompletableFuture(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            client.getBucket(request, new AsyncHandler<>() {
                @Override
                public void onSuccess(GetBucketRequest ignored, GetBucketResponse response) {
                    future.complete(true);
                }

                @Override
                public void onError(GetBucketRequest ignored, Throwable error) {
                    Throwable cause = unwrap(error);
                    if (cause instanceof BmcException bmcException && bmcException.getStatusCode() == 404) {
                        future.complete(false);
                        return;
                    }
                    String msg = String.format("Error retrieving bucket with name [%s] in namespace [%s]", name, configuration.getNamespace());
                    future.completeExceptionally(new ObjectStorageException(msg, cause));
                }
            });
            return future;
        });
    }

    private <REQUEST, RESPONSE, RESULT> CompletableFuture<RESULT> invoke(Invocation<REQUEST, RESPONSE> invocation,
                                                                         SuccessMapper<RESPONSE, RESULT> successMapper,
                                                                         ErrorMapper<RESULT> errorMapper) {
        CompletableFuture<RESULT> future = new CompletableFuture<>();
        invocation.invoke(new AsyncHandler<>() {
            @Override
            public void onSuccess(REQUEST request, RESPONSE response) {
                future.complete(successMapper.map(response));
            }

            @Override
            public void onError(REQUEST request, Throwable error) {
                Throwable cause = unwrap(error);
                RuntimeException mapped = errorMapper.map(cause);
                if (mapped == null) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(mapped);
                }
            }
        });
        return future;
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completionException && completionException.getCause() != null
            ? completionException.getCause()
            : throwable;
    }

    @FunctionalInterface
    private interface Invocation<REQUEST, RESPONSE> {
        void invoke(AsyncHandler<REQUEST, RESPONSE> handler);
    }

    @FunctionalInterface
    private interface SuccessMapper<RESPONSE, RESULT> {
        RESULT map(RESPONSE response);
    }

    @FunctionalInterface
    private interface ErrorMapper<RESULT> {
        RuntimeException map(Throwable throwable);
    }
}
