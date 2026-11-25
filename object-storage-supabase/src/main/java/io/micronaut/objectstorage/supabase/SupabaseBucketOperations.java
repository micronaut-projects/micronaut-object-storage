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
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.supabase.SupabaseConfiguration;
import io.micronaut.supabase.storage.BucketSchema;
import io.micronaut.supabase.storage.SupabaseStorageClient;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Supabase Storage bucket operations.
 *
 * <p>Buckets are created private with no size or MIME type limits; use the {@link SupabaseStorageClient}
 * directly for other settings. Supabase Storage only deletes empty buckets.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
@EachBean(SupabaseStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = SupabaseStorageConfiguration.class)
public class SupabaseBucketOperations implements BucketOperations<BucketSchema> {

    private final SupabaseStorageClient client;
    private final SupabaseConfiguration supabaseConfiguration;

    /**
     * @param configuration Object storage configuration
     * @param client Supabase Storage API client
     * @param supabaseConfiguration Supabase project configuration
     */
    public SupabaseBucketOperations(@Parameter SupabaseStorageConfiguration configuration,
                                    SupabaseStorageClient client,
                                    SupabaseConfiguration supabaseConfiguration) {
        this.client = client;
        this.supabaseConfiguration = supabaseConfiguration;
    }

    @Override
    public void create(@NonNull String name) {
        String authorization = getAuthorization();
        SupabaseStorageCalls.execute(
            String.format("Error when trying to create a bucket with name [%s] on Supabase Storage", name),
            () -> client.createBucket(authorization, SupabaseStorageCalls.createBucketRequest(name)));
    }

    @Override
    @NonNull
    public Optional<BucketEntry<BucketSchema>> retrieve(@NonNull String name) {
        String authorization = getAuthorization();
        try {
            BucketSchema bucket = SupabaseStorageCalls.execute(
                String.format("Error when trying to retrieve a bucket with name [%s] from Supabase Storage", name),
                () -> client.getBucket(authorization, name)).body();
            return Optional.of(new BucketEntry<>(name, bucket));
        } catch (SupabaseStorageException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(@NonNull String name) {
        String authorization = getAuthorization();
        SupabaseStorageCalls.execute(
            String.format("Error when trying to delete a bucket with name [%s] on Supabase Storage", name),
            () -> client.deleteBucket(authorization, name));
    }

    /**
     * @return The {@code Authorization} header value sent with every request
     */
    @NonNull
    protected String getAuthorization() {
        return SupabaseStorageCalls.authorization(supabaseConfiguration);
    }
}
