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
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.metadata.BucketMetadataEntry;
import io.micronaut.objectstorage.metadata.BucketMetadataOperations;
import io.micronaut.objectstorage.metadata.BucketMetadataWrite;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.Lock;

/**
 * Local bucket metadata operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
final class LocalStorageBucketMetadataOperations implements BucketMetadataOperations<Path> {

    private final LocalStorageLayout layout;
    private final boolean supportsPosixPermissions;

    LocalStorageBucketMetadataOperations(@Parameter LocalStorageConfiguration configuration) {
        this.layout = new LocalStorageLayout(configuration);
        layout.requireBucketRoot("bucket metadata operations");
        this.supportsPosixPermissions = layout.storageRoot().getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @Override
    @NonNull
    public Optional<BucketMetadataEntry<Path>> retrieve(@NonNull String name) {
        Path metadataFile = metadataFilePath(name);
        try {
            return Optional.of(LocalStorageMetadataSupport.readBucketMetadata(metadataFile, name));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading metadata for bucket: " + name, e);
        }
    }

    @Override
    public void save(@NonNull BucketMetadataWrite write) {
        Path bucketPath = layout.bucketPath(write.name());
        Lock bucketLock = LocalStorageLocks.bucketReadLock(bucketPath);
        bucketLock.lock();
        try {
            if (!Files.isDirectory(bucketPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new ObjectStorageException("Cannot persist metadata for a missing bucket: " + write.name());
            }
            Path metadataFile = metadataFilePath(write.name());
            if (!LocalStorageIoSupport.mkdirs(layout.rootInternalDirectory(), metadataFile.getParent(), supportsPosixPermissions)) {
                throw new ObjectStorageException("Error creating metadata directories for bucket: " + write.name());
            }
            Path temporaryDirectory = prepareTemporaryDirectory(write.name());
            Properties properties = LocalStorageMetadataSupport.toProperties(write);
            try {
                LocalStorageIoSupport.writeAndReplace(
                    metadataFile,
                    temporaryDirectory,
                    "micronaut-object-storage-local-bucket-metadata",
                    ".tmp",
                    supportsPosixPermissions,
                    metadataOut -> properties.store(metadataOut, "Metadata for bucket: " + write.name())
                );
            } catch (IOException e) {
                throw new ObjectStorageException("Error storing metadata for bucket: " + write.name(), e);
            }
        } finally {
            bucketLock.unlock();
        }
    }

    @Override
    public void delete(@NonNull String name) {
        Path metadataFile = metadataFilePath(name);
        Lock bucketLock = LocalStorageLocks.bucketReadLock(layout.bucketPath(name));
        bucketLock.lock();
        try {
            try {
                Files.delete(metadataFile);
            } catch (NoSuchFileException e) {
                // Missing metadata is the only absence signal. Other I/O failures must be reported.
            } catch (IOException e) {
                throw new ObjectStorageException("Error deleting metadata for bucket: " + name, e);
            }
        } finally {
            bucketLock.unlock();
        }
    }

    private Path metadataFilePath(String name) {
        return layout.bucketMetadataFile(name);
    }

    private Path prepareTemporaryDirectory(String name) {
        Path temporaryDirectory = layout.bucketMetadataTemporaryDirectory(name);
        try {
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), temporaryDirectory);
            if (!LocalStorageIoSupport.mkdirs(layout.rootInternalDirectory(), temporaryDirectory, supportsPosixPermissions)) {
                throw new ObjectStorageException("Error creating temporary metadata directories for bucket: " + name);
            }
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), temporaryDirectory);
            return temporaryDirectory;
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Error preparing temporary metadata directory for bucket: " + name, e);
        }
    }
}
