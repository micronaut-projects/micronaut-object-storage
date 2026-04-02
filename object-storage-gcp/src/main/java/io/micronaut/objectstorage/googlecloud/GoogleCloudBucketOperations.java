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
package io.micronaut.objectstorage.googlecloud;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * GCP bucket operations.
 *
 * @since 3.1.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(GoogleCloudStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = GoogleCloudStorageConfiguration.class)
public class GoogleCloudBucketOperations implements BucketOperations<Bucket> {
    private final Storage storage;

    public GoogleCloudBucketOperations(@Parameter GoogleCloudStorageConfiguration configuration,
                                       Storage storage) {
        this.storage = storage;
    }

    @Override
    public void create(@NonNull String name) {
        storage.create(BucketInfo.newBuilder(name).build());
    }

    @Override
    public Optional<BucketEntry<Bucket>> retrieve(@NonNull String name) {
        Bucket bucket = storage.get(name);
        return bucket == null ? Optional.empty() : Optional.of(new BucketEntry<>(name, bucket));
    }

    @Override
    public void delete(@NonNull String name) {
        storage.delete(name);
    }
}
