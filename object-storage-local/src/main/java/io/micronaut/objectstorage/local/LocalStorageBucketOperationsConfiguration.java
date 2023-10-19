package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import java.nio.file.Path;

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
