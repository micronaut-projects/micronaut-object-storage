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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.metadata.BucketMetadataOperations;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * Local bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
final class LocalStorageBucketOperations implements BucketOperations<Path> {
    private final LocalStorageLayout layout;
    private final BucketMetadataOperations<Path> bucketMetadataOperations;

    LocalStorageBucketOperations(@Parameter LocalStorageConfiguration configuration,
                                 BucketMetadataOperations<Path> bucketMetadataOperations) {
        this.layout = new LocalStorageLayout(configuration);
        this.bucketMetadataOperations = bucketMetadataOperations;
        layout.requireBucketRoot("bucket operations");
    }

    @Override
    public void create(@NonNull String name) {
        Path path = layout.bucketPath(name);
        Lock bucketLock = LocalStorageLocks.bucketWriteLock(path);
        bucketLock.lock();
        try {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } finally {
            bucketLock.unlock();
        }
    }

    @Override
    @NonNull
    public Optional<BucketEntry<Path>> retrieve(@NonNull String name) {
        Path path = layout.bucketPath(name);
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        }
        return Optional.of(new BucketEntry<>(name, path));
    }

    @Override
    public void delete(@NonNull String name) {
        Path path = layout.bucketPath(name);
        Lock bucketLock = LocalStorageLocks.bucketWriteLock(path);
        bucketLock.lock();
        try {
            try {
                try {
                    deleteRecursively(path);
                } catch (NoSuchFileException ignored) {
                    // Deleting a missing bucket is a no-op for user files, but provider state can still be stale.
                }
                deleteProviderManagedBucketState(name);
                bucketMetadataOperations.delete(name);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } finally {
            bucketLock.unlock();
        }
    }

    private void deleteProviderManagedBucketState(String name) throws IOException {
        for (Path directory : layout.providerManagedBucketDirectories(name)) {
            deleteRecursivelyIfExists(directory);
        }
    }

    private static void deleteRecursivelyIfExists(Path path) throws IOException {
        try {
            deleteRecursively(path);
        } catch (NoSuchFileException ignored) {
            // The bucket may not have provider-managed object metadata or snapshots.
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            boolean topIsDirectory = false;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                topIsDirectory = true;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!topIsDirectory) {
                    // if `path` is not a directory, fail
                    throw new IllegalStateException("Not a directory: " + file);
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
