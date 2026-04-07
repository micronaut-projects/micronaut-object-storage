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
package io.micronaut.objectstorage.googlecloud;

import com.google.cloud.storage.Bucket;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import io.micronaut.objectstorage.internal.DefaultReactiveBucketOperations;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Reactive Google Cloud bucket operations.
 *
 * The Google Cloud Storage client used on this branch exposes blocking bucket lifecycle methods only,
 * so this bean intentionally keeps the executor-backed bridge until the SDK offers a native async bucket API.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(GoogleCloudStorageConfiguration.class)
@Requires(beans = GoogleCloudStorageConfiguration.class)
@Requires(beans = GoogleCloudBucketOperations.class)
public class GoogleCloudReactiveBucketOperations implements ReactiveBucketOperations<Bucket> {

    private final DefaultReactiveBucketOperations<Bucket> delegate;

    public GoogleCloudReactiveBucketOperations(GoogleCloudBucketOperations operations,
                                               @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        this.delegate = new DefaultReactiveBucketOperations<>(operations, blockingExecutor);
    }

    @Override
    @NonNull
    public Publisher<Void> create(@NonNull String name) {
        return delegate.create(name);
    }

    @Override
    @NonNull
    public Publisher<Optional<BucketEntry<Bucket>>> retrieve(@NonNull String name) {
        return delegate.retrieve(name);
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String name) {
        return delegate.delete(name);
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String name) {
        return delegate.exists(name);
    }
}
