package io.micronaut.objectstorage.bucket;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageOperations;

import java.util.Set;

public interface BucketOperations<I, O, D> {
    /**
     * List the available buckets. This operation may be blocking.
     *
     * @return The available buckets
     */
    @Blocking
    @NonNull
    Set<String> listBuckets();

    /**
     * Create an {@link ObjectStorageOperations} implementation to access data in the given bucket.
     * <br>
     * If the bucket does not exist, this operation <i>may</i> fail, but usually it will only fail
     * when trying to access the bucket contents.
     *
     * @param bucket The bucket name as returned by {@link #listBuckets()}
     * @return An {@link ObjectStorageOperations} for the given bucket
     */
    @NonNull
    ObjectStorageOperations<I, O, D> storageForBucket(@NonNull String bucket);
}
