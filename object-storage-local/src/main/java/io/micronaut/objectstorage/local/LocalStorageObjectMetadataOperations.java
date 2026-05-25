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
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry;
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * Local object metadata operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
@Secondary
final class LocalStorageObjectMetadataOperations implements ObjectMetadataOperations<Path> {

    private final Path bucketPath;
    private final Path metadataRoot;
    private final boolean supportsPosixPermissions;

    LocalStorageObjectMetadataOperations(@Parameter LocalStorageConfiguration configuration) {
        this.bucketPath = configuration.getPath();
        this.metadataRoot = bucketPath
            .resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.METADATA_DIRECTORY);
        this.supportsPosixPermissions = bucketPath.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @Override
    @NonNull
    public Optional<ObjectMetadataEntry<Path>> retrieve(@NonNull String key) {
        Path metadataFile = metadataFilePath(key);
        if (!Files.exists(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalStorageMetadataSupport.readObjectMetadata(metadataFile, key));
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading metadata for object: " + key, e);
        }
    }

    @Override
    public void save(@NonNull ObjectMetadataWrite write) {
        Path objectFile = LocalStorageIoSupport.resolveSafe(bucketPath, write.key());
        if (!Files.exists(objectFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new ObjectStorageException("Cannot persist metadata for a missing object: " + write.key());
        }
        Path metadataFile = prepareMetadataTarget(write.key());
        ObjectMetadataWrite effectiveWrite = enrich(write, objectFile);
        Properties properties = LocalStorageMetadataSupport.toProperties(effectiveWrite);
        try (OutputStream metadataOut = LocalStorageIoSupport.newOutputStreamNoFollow(metadataFile, supportsPosixPermissions)) {
            properties.store(metadataOut, "Metadata for file: " + write.key());
        } catch (IOException e) {
            throw new ObjectStorageException("Error storing metadata for object: " + write.key(), e);
        }
    }

    @Override
    public void delete(@NonNull String key) {
        Path metadataFile = metadataFilePath(key);
        if (Files.exists(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.delete(metadataFile);
            } catch (IOException e) {
                throw new ObjectStorageException("Error deleting metadata for object: " + key, e);
            }
        }
    }

    private Path metadataFilePath(String key) {
        LocalStorageIoSupport.rejectSymbolicLinks(bucketPath, metadataRoot);
        return LocalStorageIoSupport.resolveSafe(metadataRoot, key);
    }

    private Path prepareMetadataTarget(String key) {
        Path metadataFile = metadataFilePath(key);
        if (!LocalStorageIoSupport.mkdirs(metadataRoot, metadataFile.getParent(), supportsPosixPermissions)) {
            throw new ObjectStorageException("Error creating metadata directories for object: " + key);
        }
        return metadataFile;
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
