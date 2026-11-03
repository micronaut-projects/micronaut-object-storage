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
package io.micronaut.objectstorage.supabase;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.supabase.SupabaseConfiguration;
import io.micronaut.supabase.storage.BucketSchema;
import io.micronaut.supabase.storage.ReactiveSupabaseStorageClient;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Reactive Supabase Storage bucket operations, built on the non-blocking Micronaut Supabase
 * {@link ReactiveSupabaseStorageClient}.
 *
 * <p>Buckets are created private with no size or MIME type limits; use the {@link ReactiveSupabaseStorageClient}
 * directly for other settings. Supabase Storage only deletes empty buckets. Nothing is sent until a returned
 * publisher is subscribed to, and every failure is signalled as an error.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
@EachBean(SupabaseStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = SupabaseStorageConfiguration.class)
@Requires(beans = ReactiveSupabaseStorageClient.class)
public class SupabaseReactiveBucketOperations implements ReactiveBucketOperations<BucketSchema> {

    private final ReactiveSupabaseStorageClient client;
    private final SupabaseConfiguration supabaseConfiguration;

    /**
     * @param configuration Object storage configuration
     * @param client Reactive Supabase Storage API client
     * @param supabaseConfiguration Supabase project configuration
     */
    public SupabaseReactiveBucketOperations(@Parameter SupabaseStorageConfiguration configuration,
                                            ReactiveSupabaseStorageClient client,
                                            SupabaseConfiguration supabaseConfiguration) {
        this.client = client;
        this.supabaseConfiguration = supabaseConfiguration;
    }

    @Override
    @NonNull
    public Publisher<Void> create(@NonNull String name) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to create a bucket with name [%s] on Supabase Storage", name),
                () -> client.createBucket(authorization, SupabaseStorageCalls.createBucketRequest(name)))
            .then());
    }

    @Override
    @NonNull
    public Publisher<Optional<BucketEntry<BucketSchema>>> retrieve(@NonNull String name) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to retrieve a bucket with name [%s] from Supabase Storage", name),
                () -> client.getBucket(authorization, name))
            .map(response -> Optional.of(new BucketEntry<>(name, response.body())))
            .onErrorResume(SupabaseStorageException.class, e -> e.isNotFound()
                ? Mono.just(Optional.<BucketEntry<BucketSchema>>empty())
                : Mono.error(e)));
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String name) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to delete a bucket with name [%s] on Supabase Storage", name),
                () -> client.deleteBucket(authorization, name))
            .then());
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String name) {
        return withAuthorization(authorization -> SupabaseStorageCalls.executeReactive(
                String.format("Error when trying to retrieve a bucket with name [%s] from Supabase Storage", name),
                () -> client.getBucket(authorization, name))
            .map(response -> Boolean.TRUE)
            .onErrorResume(SupabaseStorageException.class, e -> e.isNotFound() ? Mono.just(Boolean.FALSE) : Mono.error(e)));
    }

    /**
     * @return The {@code Authorization} header value sent with every request
     */
    @NonNull
    protected String getAuthorization() {
        return SupabaseStorageCalls.authorization(supabaseConfiguration);
    }

    private <T> Mono<T> withAuthorization(Function<String, Mono<T>> call) {
        return Mono.defer(() -> call.apply(getAuthorization()));
    }
}
