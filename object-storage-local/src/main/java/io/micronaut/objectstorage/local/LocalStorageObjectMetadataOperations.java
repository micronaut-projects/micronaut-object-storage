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
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry;
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.Lock;

/**
 * Local object metadata operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
final class LocalStorageObjectMetadataOperations implements ObjectMetadataOperations<Path> {

    private final LocalStorageLayout layout;
    private final LocalStorageMetadataMode metadataMode;
    private final boolean supportsPosixPermissions;

    LocalStorageObjectMetadataOperations(@Parameter LocalStorageConfiguration configuration) {
        this.layout = new LocalStorageLayout(configuration);
        this.metadataMode = configuration.getMetadataMode();
        this.supportsPosixPermissions = layout.storageRoot().getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @Override
    @NonNull
    public Optional<ObjectMetadataEntry<Path>> retrieve(@NonNull String key) {
        if (metadataMode == LocalStorageMetadataMode.NONE) {
            return Optional.empty();
        }
        for (Path metadataFile : layout.objectMetadataReadPaths(key)) {
            try {
                return Optional.of(LocalStorageMetadataSupport.readObjectMetadata(metadataFile, key));
            } catch (NoSuchFileException e) {
                // Missing metadata is the only absence signal. Other I/O failures must not fall through to stale sidecars.
                continue;
            } catch (IOException e) {
                throw new ObjectStorageException("Error reading metadata for object: " + key, e);
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(@NonNull ObjectMetadataWrite write) {
        if (metadataMode == LocalStorageMetadataMode.NONE) {
            throw new ObjectStorageException("Local storage metadata mode NONE does not support object metadata persistence");
        }
        Lock bucketLock = LocalStorageLocks.bucketReadLock(layout.configuredBucketPath());
        bucketLock.lock();
        try {
            Path objectFile = layout.objectPath(write.key());
            if (!Files.exists(objectFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new ObjectStorageException("Cannot persist metadata for a missing object: " + write.key());
            }
            Path metadataFile = prepareMetadataTarget(write.key());
            Path temporaryDirectory = prepareTemporaryDirectory(write.key());
            ObjectMetadataWrite effectiveWrite = enrich(write, objectFile);
            Properties properties = LocalStorageMetadataSupport.toProperties(effectiveWrite);
            try {
                LocalStorageIoSupport.writeAndReplace(
                    metadataFile,
                    temporaryDirectory,
                    "micronaut-object-storage-local-metadata",
                    ".tmp",
                    supportsPosixPermissions,
                    metadataOut -> properties.store(metadataOut, "Metadata for file: " + write.key())
                );
            } catch (IOException e) {
                throw new ObjectStorageException("Error storing metadata for object: " + write.key(), e);
            }
        } finally {
            bucketLock.unlock();
        }
    }

    @Override
    public void delete(@NonNull String key) {
        Lock bucketLock = LocalStorageLocks.bucketReadLock(layout.configuredBucketPath());
        bucketLock.lock();
        try {
            List<IOException> failures = new ArrayList<>();
            for (Path metadataFile : layout.objectMetadataDeletePaths(key)) {
                try {
                    Files.delete(metadataFile);
                } catch (NoSuchFileException e) {
                    // Missing metadata is the only absence signal. Other I/O failures must be reported.
                    continue;
                } catch (IOException e) {
                    failures.add(e);
                }
            }
            if (!failures.isEmpty()) {
                IOException failure = failures.remove(0);
                failures.forEach(failure::addSuppressed);
                throw new ObjectStorageException("Error deleting metadata for object: " + key, failure);
            }
        } finally {
            bucketLock.unlock();
        }
    }

    private Path prepareMetadataTarget(String key) {
        Path metadataFile = layout.objectMetadataFile(key);
        if (!LocalStorageIoSupport.mkdirs(layout.rootInternalDirectory(), metadataFile.getParent(), supportsPosixPermissions)) {
            throw new ObjectStorageException("Error creating metadata directories for object: " + key);
        }
        return metadataFile;
    }

    private Path prepareTemporaryDirectory(String key) {
        Path temporaryDirectory = layout.objectMetadataTemporaryDirectory();
        try {
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), temporaryDirectory);
            if (!LocalStorageIoSupport.mkdirs(layout.rootInternalDirectory(), temporaryDirectory, supportsPosixPermissions)) {
                throw new ObjectStorageException("Error creating temporary metadata directories for object: " + key);
            }
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), temporaryDirectory);
            return temporaryDirectory;
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Error preparing temporary metadata directory for object: " + key, e);
        }
    }

    private ObjectMetadataWrite enrich(ObjectMetadataWrite write, Path objectFile) {
        try {
            Long contentLength = write.contentLength() != null ? write.contentLength() : Files.size(objectFile);
            Instant lastModified = write.lastModified() != null
                ? write.lastModified()
                : Files.getLastModifiedTime(objectFile, LinkOption.NOFOLLOW_LINKS).toInstant();
            return new ObjectMetadataWrite(
                write.key(),
                write.metadata(),
                write.attributes(),
                write.contentType(),
                contentLength,
                write.etag(),
                lastModified
            );
        } catch (IOException e) {
            throw new ObjectStorageException("Error inspecting object while saving metadata: " + write.key(), e);
        }
    }
}
