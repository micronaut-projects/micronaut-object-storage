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
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry;
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import io.micronaut.objectstorage.metadata.ReactiveObjectMetadataOperations;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Executor-backed reactive adapter for the blocking object metadata contract.
 *
 * @param <T> The provider-native metadata representation type.
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@Internal
public class DefaultReactiveObjectMetadataOperations<T> implements ReactiveObjectMetadataOperations<T> {

    private final ObjectMetadataOperations<T> delegate;
    private final ExecutorService blockingExecutor;

    public DefaultReactiveObjectMetadataOperations(ObjectMetadataOperations<T> delegate,
                                                   ExecutorService blockingExecutor) {
        this.delegate = delegate;
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    @NonNull
    public Publisher<Optional<ObjectMetadataEntry<T>>> retrieve(@NonNull String key) {
        return supply(() -> delegate.retrieve(key));
    }

    @Override
    @NonNull
    public Publisher<Void> save(@NonNull ObjectMetadataWrite write) {
        return Publishers.onComplete(
            Publishers.empty(),
            () -> CompletableFuture.runAsync(() -> delegate.save(write), blockingExecutor)
                .thenApply(ignored -> null)
        );
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String key) {
        return Publishers.onComplete(
            Publishers.empty(),
            () -> CompletableFuture.runAsync(() -> delegate.delete(key), blockingExecutor)
                .thenApply(ignored -> null)
        );
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String key) {
        return supply(() -> delegate.exists(key));
    }

    private <E> Publisher<E> supply(Supplier<E> supplier) {
        return Publishers.fromCompletableFuture(() -> CompletableFuture.supplyAsync(supplier, blockingExecutor));
    }
}
