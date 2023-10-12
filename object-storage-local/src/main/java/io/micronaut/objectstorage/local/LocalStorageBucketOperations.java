package io.micronaut.objectstorage.local;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
final class LocalStorageBucketOperations implements BucketOperations<
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile> {
    private final List<LocalStorageOperations> configuredStores;

    public LocalStorageBucketOperations(@NonNull List<LocalStorageOperations> configuredStores) {
        this.configuredStores = configuredStores;
    }

    @Override
    public Set<String> listBuckets() {
        return configuredStores.stream()
            .map(LocalStorageBucketOperations::identifier)
            .collect(Collectors.toSet());
    }

    @Override
    public ObjectStorageOperations<LocalStorageOperations.LocalStorageFile, LocalStorageOperations.LocalStorageFile, LocalStorageOperations.LocalStorageFile> storageForBucket(String bucket) {
        // we only permit configured buckets to prevent access to the whole file system
        return configuredStores.stream()
            .filter(lso -> identifier(lso).equals(bucket))
            .findFirst().orElseThrow(() -> new NoSuchElementException("No such bucket configured. Please create it under the " + LocalStorageConfiguration.PREFIX + " config prefix: " + bucket));
    }

    private static String identifier(LocalStorageOperations lso) {
        return lso.configuration.getPath().toAbsolutePath().toString();
    }
}
