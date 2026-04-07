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
package io.micronaut.objectstorage.azure;

import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.models.BlobContainerProperties;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Reactive Azure bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(BlobServiceAsyncClient.class)
@Requires(beans = BlobServiceAsyncClient.class)
public class AzureBlobReactiveBucketOperations implements ReactiveBucketOperations<BlobContainerProperties> {

    private final AzureAsyncBucketClient client;

    public AzureBlobReactiveBucketOperations(BlobServiceAsyncClient blobServiceAsyncClient) {
        this(new BlobServiceAzureAsyncBucketClient(blobServiceAsyncClient));
    }

    AzureBlobReactiveBucketOperations(AzureAsyncBucketClient client) {
        this.client = client;
    }

    @Override
    @NonNull
    public Publisher<Void> create(@NonNull String name) {
        return client.create(name);
    }

    @Override
    @NonNull
    public Publisher<Optional<BucketEntry<BlobContainerProperties>>> retrieve(@NonNull String name) {
        return client.retrieve(name);
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String name) {
        return client.delete(name);
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String name) {
        return client.exists(name);
    }

    interface AzureAsyncBucketClient {
        Publisher<Void> create(@NonNull String name);

        Publisher<Optional<BucketEntry<BlobContainerProperties>>> retrieve(@NonNull String name);

        Publisher<Void> delete(@NonNull String name);

        Publisher<Boolean> exists(@NonNull String name);
    }

    private static final class BlobServiceAzureAsyncBucketClient implements AzureAsyncBucketClient {
        private final BlobServiceAsyncClient blobServiceAsyncClient;

        private BlobServiceAzureAsyncBucketClient(BlobServiceAsyncClient blobServiceAsyncClient) {
            this.blobServiceAsyncClient = blobServiceAsyncClient;
        }

        @Override
        public Publisher<Void> create(@NonNull String name) {
            return Mono.defer(() -> blobServiceAsyncClient.createBlobContainer(name).then());
        }

        @Override
        public Publisher<Optional<BucketEntry<BlobContainerProperties>>> retrieve(@NonNull String name) {
            return Mono.defer(() -> {
                BlobContainerAsyncClient containerAsyncClient = blobServiceAsyncClient.getBlobContainerAsyncClient(name);
                return containerAsyncClient.exists()
                    .flatMap(exists -> exists
                        ? containerAsyncClient.getProperties()
                            .map(properties -> Optional.of(new BucketEntry<>(name, properties)))
                        : Mono.just(Optional.empty()));
            });
        }

        @Override
        public Publisher<Void> delete(@NonNull String name) {
            return Mono.defer(() -> blobServiceAsyncClient.getBlobContainerAsyncClient(name).delete());
        }

        @Override
        public Publisher<Boolean> exists(@NonNull String name) {
            return Mono.defer(() -> blobServiceAsyncClient.getBlobContainerAsyncClient(name).exists());
        }
    }
}
