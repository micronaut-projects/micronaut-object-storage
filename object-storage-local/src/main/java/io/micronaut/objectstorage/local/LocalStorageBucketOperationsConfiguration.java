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
package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import java.nio.file.Path;

/**
 * Configuration for local {@link io.micronaut.objectstorage.bucket.BucketOperations}. This is
 * separate from the config of {@link io.micronaut.objectstorage.ObjectStorageOperations}.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@ConfigurationProperties(LocalStorageBucketOperationsConfiguration.PREFIX)
public class LocalStorageBucketOperationsConfiguration {
    static final String PREFIX = ObjectStorageConfiguration.PREFIX + ".local-bucket-operations";

    static final String BUCKET_OPERATIONS_DIRECTORY = LocalStorageBucketOperationsConfiguration.PREFIX + ".directory";

    private Path directory;

    /**
     * Path for {@link io.micronaut.objectstorage.bucket.BucketOperations#createBucket(String) creating}
     * new buckets.
     *
     * @return The path to use for creating new buckets
     */
    @Nullable
    public Path getDirectory() {
        return directory;
    }

    /**
     * Path for {@link io.micronaut.objectstorage.bucket.BucketOperations#createBucket(String) creating}
     * new buckets.
     *
     * @param directory The path to use for creating new buckets
     */
    public void setDirectory(@Nullable Path directory) {
        this.directory = directory;
    }
}
