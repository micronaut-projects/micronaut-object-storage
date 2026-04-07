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
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Executor-backed reactive adapter for the blocking bucket/container contract.
 *
 * @param <T> The provider-native bucket/container representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@Internal
public class DefaultReactiveBucketOperations<T> implements ReactiveBucketOperations<T> {

    private final BucketOperations<T> delegate;
    private final ExecutorService blockingExecutor;

    public DefaultReactiveBucketOperations(BucketOperations<T> delegate,
                                           ExecutorService blockingExecutor) {
        this.delegate = delegate;
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    @NonNull
    public Publisher<Void> create(@NonNull String name) {
        return Publishers.onComplete(
            Publishers.empty(),
            () -> CompletableFuture.runAsync(() -> delegate.create(name), blockingExecutor)
                .thenApply(ignored -> null)
        );
    }

    @Override
    @NonNull
    public Publisher<Optional<BucketEntry<T>>> retrieve(@NonNull String name) {
        return supply(() -> delegate.retrieve(name));
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String name) {
        return Publishers.onComplete(
            Publishers.empty(),
            () -> CompletableFuture.runAsync(() -> delegate.delete(name), blockingExecutor)
                .thenApply(ignored -> null)
        );
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String name) {
        return supply(() -> delegate.exists(name));
    }

    private <E> Publisher<E> supply(Supplier<E> supplier) {
        return Publishers.fromCompletableFuture(() -> CompletableFuture.supplyAsync(supplier, blockingExecutor));
    }
}
