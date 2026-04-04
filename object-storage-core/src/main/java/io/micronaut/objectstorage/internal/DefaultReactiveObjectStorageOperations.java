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
package io.micronaut.objectstorage.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Executor-backed reactive adapter for the blocking object storage contract.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 * @param <I> Cloud vendor-specific upload request class or builder
 * @param <O> Cloud vendor-specific upload response
 * @param <D> Cloud vendor-specific delete response
 */
@Internal
public class DefaultReactiveObjectStorageOperations<I, O, D> implements ReactiveObjectStorageOperations<I, O, D> {

    private final ObjectStorageOperations<I, O, D> delegate;
    private final ExecutorService blockingExecutor;

    public DefaultReactiveObjectStorageOperations(ObjectStorageOperations<I, O, D> delegate,
                                                  ExecutorService blockingExecutor) {
        this.delegate = delegate;
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<O>> upload(@NonNull UploadRequest request) {
        return supply(() -> delegate.upload(request));
    }

    @Override
    @NonNull
    public Publisher<UploadResponse<O>> upload(@NonNull UploadRequest request, @NonNull Consumer<I> requestConsumer) {
        return supply(() -> delegate.upload(request, requestConsumer));
    }

    @Override
    @NonNull
    public <E extends ObjectStorageEntry<?>> Publisher<Optional<E>> retrieve(@NonNull String key) {
        return supply(() -> delegate.retrieve(key));
    }

    @Override
    @NonNull
    public Publisher<D> delete(@NonNull String key) {
        return supply(() -> delegate.delete(key));
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String key) {
        return supply(() -> delegate.exists(key));
    }

    @Override
    @NonNull
    public Publisher<Set<String>> listObjects() {
        return supply(delegate::listObjects);
    }

    @Override
    @NonNull
    public Publisher<ListObjectsResponse> listObjects(@NonNull ListObjectsRequest request) {
        return supply(() -> delegate.listObjects(request));
    }

    @Override
    @NonNull
    public Publisher<Void> copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        return Publishers.onComplete(
            Publishers.empty(),
            () -> CompletableFuture.runAsync(() -> delegate.copy(sourceKey, destinationKey), blockingExecutor)
                .thenApply(ignored -> null)
        );
    }

    private <T> Publisher<T> supply(Supplier<T> supplier) {
        return Publishers.fromCompletableFuture(() -> CompletableFuture.supplyAsync(supplier, blockingExecutor));
    }
}
