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

/**
 * Local bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
final class LocalStorageBucketOperations implements BucketOperations<Path> {
    private final Path rootDirectory;
    private final LocalStorageBucketMetadataOperations bucketMetadataOperations;

    LocalStorageBucketOperations(@Parameter LocalStorageConfiguration configuration,
                                 LocalStorageBucketMetadataOperations bucketMetadataOperations) {
        this.rootDirectory = configuration.getPath().toAbsolutePath().normalize().getParent();
        this.bucketMetadataOperations = bucketMetadataOperations;
        if (rootDirectory == null) {
            throw new IllegalArgumentException("Local storage bucket operations require a bucket path with a parent directory");
        }
    }

    @Override
    public void create(@NonNull String name) {
        Path path = LocalStorageIoSupport.resolveBucketPath(rootDirectory, name);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @NonNull
    public Optional<BucketEntry<Path>> retrieve(@NonNull String name) {
        Path path = LocalStorageIoSupport.resolveBucketPath(rootDirectory, name);
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        }
        return Optional.of(new BucketEntry<>(name, path));
    }

    @Override
    public void delete(@NonNull String name) {
        Path path = LocalStorageIoSupport.resolveBucketPath(rootDirectory, name);
        bucketMetadataOperations.delete(name);
        try {
            deleteRecursively(path);
        } catch (NoSuchFileException ignored) {
            // Deleting a missing bucket is a no-op.
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
}
