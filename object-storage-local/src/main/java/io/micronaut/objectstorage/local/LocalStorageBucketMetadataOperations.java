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
package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.metadata.BucketMetadataEntry;
import io.micronaut.objectstorage.metadata.BucketMetadataOperations;
import io.micronaut.objectstorage.metadata.BucketMetadataWrite;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Local bucket metadata operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
@Secondary
final class LocalStorageBucketMetadataOperations implements BucketMetadataOperations<Path> {

    private static final String BUCKETS_DIRECTORY = "buckets";

    private final Path rootDirectory;
    private final Path metadataDirectory;
    private final Path metadataRoot;
    private final boolean supportsPosixPermissions;

    LocalStorageBucketMetadataOperations(@Parameter LocalStorageConfiguration configuration) {
        Path configuredBucketPath = configuration.getPath().toAbsolutePath().normalize();
        this.rootDirectory = configuredBucketPath.getParent();
        if (rootDirectory == null) {
            throw new IllegalArgumentException("Local storage bucket metadata operations require a bucket path with a parent directory");
        }
        this.metadataDirectory = rootDirectory
            .resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.METADATA_DIRECTORY);
        this.metadataRoot = metadataDirectory.resolve(BUCKETS_DIRECTORY);
        this.supportsPosixPermissions = rootDirectory.getFileSystem().supportedFileAttributeViews().contains("posix");
        if (!LocalStorageIoSupport.mkdirs(metadataDirectory, metadataRoot, supportsPosixPermissions)) {
            throw new ObjectStorageException("Error creating bucket metadata directory: " + metadataRoot);
        }
    }

    @Override
    @NonNull
    public Optional<BucketMetadataEntry<Path>> retrieve(@NonNull String name) {
        Path metadataFile = metadataFilePath(name);
        if (!Files.exists(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalStorageMetadataSupport.readBucketMetadata(metadataFile, name));
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading metadata for bucket: " + name, e);
        }
    }

    @Override
    public void save(@NonNull BucketMetadataWrite write) {
        Path bucketPath = LocalStorageIoSupport.resolveBucketPath(rootDirectory, write.name());
        if (!Files.isDirectory(bucketPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ObjectStorageException("Cannot persist metadata for a missing bucket: " + write.name());
        }
        Path metadataFile = metadataFilePath(write.name());
        if (!LocalStorageIoSupport.mkdirs(metadataDirectory, metadataFile.getParent(), supportsPosixPermissions)) {
            throw new ObjectStorageException("Error creating metadata directories for bucket: " + write.name());
        }
        Properties properties = LocalStorageMetadataSupport.toProperties(write);
        try (OutputStream metadataOut = LocalStorageIoSupport.newOutputStreamNoFollow(metadataFile, supportsPosixPermissions)) {
            properties.store(metadataOut, "Metadata for bucket: " + write.name());
        } catch (IOException e) {
            throw new ObjectStorageException("Error storing metadata for bucket: " + write.name(), e);
        }
    }

    @Override
    public void delete(@NonNull String name) {
        Path metadataFile = metadataFilePath(name);
        if (Files.exists(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.delete(metadataFile);
            } catch (IOException e) {
                throw new ObjectStorageException("Error deleting metadata for bucket: " + name, e);
            }
        }
    }

    private Path metadataFilePath(String name) {
        LocalStorageIoSupport.resolveBucketPath(rootDirectory, name);
        LocalStorageIoSupport.rejectSymbolicLinks(rootDirectory, metadataRoot);
        return LocalStorageIoSupport.resolveSafe(metadataRoot, name);
    }
}
