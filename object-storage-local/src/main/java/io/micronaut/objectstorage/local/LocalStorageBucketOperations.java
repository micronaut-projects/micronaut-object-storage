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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Local bucket operations.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@Singleton
final class LocalStorageBucketOperations implements BucketOperations<
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile> {
    private final LocalStorageBucketOperationsConfiguration moduleConfiguration;
    private final List<LocalStorageOperations> staticStores;

    public LocalStorageBucketOperations(
        @NonNull List<LocalStorageOperations> staticStores,
        @NonNull LocalStorageBucketOperationsConfiguration moduleConfiguration
    ) {
        this.staticStores = staticStores;
        this.moduleConfiguration = moduleConfiguration;
    }

    @Override
    public void createBucket(String name) {
        Path path = resolveDynamicBucketPath(name);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolveDynamicBucketPath(String name) {
        // this method does no IO, just safety checks
        Path dynamicStorePath = moduleConfiguration.getDirectory();
        if (dynamicStorePath == null) {
            throw new IllegalStateException("Please configure " + LocalStorageBucketOperationsConfiguration.BUCKET_OPERATIONS_DIRECTORY + " to create dynamic buckets");
        }
        Path path = dynamicStorePath.resolve(name).normalize();
        if (!path.getParent().equals(dynamicStorePath) ||
            !path.getFileName().toString().equals(name)) {
            throw new IllegalArgumentException("Bucket name must not contain filesystem special characters");
        }
        return path;
    }

    @Override
    public void deleteBucket(String name) {
        if (Path.of(name).isAbsolute()) {
            throw new IllegalArgumentException("Cannot delete statically configured local storage buckets");
        }
        Path path = resolveDynamicBucketPath(name);
        try {
            deleteRecursively(path);
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    @Override
    public Set<String> listBuckets() {
        Stream<String> staticStores = this.staticStores.stream()
            .map(this::staticIdentifier);
        Path dynamicStorePath = moduleConfiguration.getDirectory();
        if (dynamicStorePath != null) {
            try (Stream<Path> list = Files.list(dynamicStorePath)) {
                return Stream.concat(staticStores, list.map(p -> p.getFileName().toString()))
                    .collect(Collectors.toSet());
            } catch (NoSuchFileException | NotDirectoryException ignored) {
                // just list static stores
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return staticStores.collect(Collectors.toSet());
    }

    @Override
    public ObjectStorageOperations<LocalStorageOperations.LocalStorageFile, LocalStorageOperations.LocalStorageFile, LocalStorageOperations.LocalStorageFile> storageForBucket(String bucket) {
        // absolute paths are static stores, relative paths are dynamic
        if (Path.of(bucket).isAbsolute()) {
            // we only permit configured buckets to prevent access to the whole file system
            return staticStores.stream()
                .filter(lso -> staticIdentifier(lso).equals(bucket))
                .findFirst().orElseThrow(() -> new NoSuchElementException("No such bucket configured. Please create it under the " + LocalStorageConfiguration.PREFIX + " config prefix: " + bucket));
        } else {
            Path path = resolveDynamicBucketPath(bucket);
            LocalStorageConfiguration mockConfig = new LocalStorageConfiguration("");
            mockConfig.setPath(path);
            return new LocalStorageOperations(mockConfig);
        }
    }

    private String staticIdentifier(LocalStorageOperations lso) {
        return lso.configuration.getPath().toAbsolutePath().toString();
    }
}
