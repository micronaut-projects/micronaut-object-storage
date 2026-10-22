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

import io.micronaut.objectstorage.ObjectStorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class LocalStorageLayout {

    static final String INTERNAL_DIRECTORY = ".mn-storage";
    static final String LEGACY_METADATA_DIRECTORY = ".metadata";
    static final String METADATA_DIRECTORY = "metadata";
    static final String OBJECTS_DIRECTORY = "objects";
    static final String BUCKETS_DIRECTORY = "buckets";
    static final String MULTIPART_DIRECTORY = "multipart";
    static final String SNAPSHOT_DIRECTORY = "snapshots";
    static final String TEMPORARY_DIRECTORY = "tmp";
    static final String OBJECT_METADATA_TEMPORARY_DIRECTORY = "object-metadata";
    static final String BUCKET_METADATA_TEMPORARY_DIRECTORY = "bucket-metadata";

    private final Path bucketPath;
    private final Path storageRoot;
    private final String bucketName;
    private final boolean hasBucketParent;
    private final String internalDirectory;

    LocalStorageLayout(LocalStorageConfiguration configuration) {
        this(configuration.getPath(), configuration.getName(), configuration.getInternalDirectory());
    }

    private LocalStorageLayout(Path bucketPath, String configurationName, String internalDirectory) {
        this.internalDirectory = internalDirectory;
        this.bucketPath = bucketPath;
        Path normalizedBucketPath = bucketPath.toAbsolutePath().normalize();
        Path bucketParent = normalizedBucketPath.getParent();
        this.hasBucketParent = bucketParent != null;
        this.storageRoot = hasBucketParent ? bucketParent : normalizedBucketPath;
        Path bucketFileName = normalizedBucketPath.getFileName();
        String reservedNamespace = bucketFileName == null ? null : reservedLocalStorageNamespace(bucketFileName.toString()).orElse(null);
        if (bucketFileName != null && internalDirectory.equalsIgnoreCase(bucketFileName.toString())) {
            reservedNamespace = internalDirectory;
        }
        if (reservedNamespace != null) {
            throw new IllegalArgumentException("Local storage bucket path uses the reserved " + reservedNamespace + " namespace: " + bucketPath);
        }
        this.bucketName = bucketFileName == null ? configurationName : bucketFileName.toString();
        validateCustomInternalDirectory();
    }

    /**
     * Recognizes the provider layout without claiming or modifying an existing user directory.
     * Subdirectories are optional because provider state is created lazily.
     */
    private void validateCustomInternalDirectory() {
        if (INTERNAL_DIRECTORY.equals(internalDirectory)) {
            return;
        }
        Path directory = rootInternalDirectory();
        LocalStorageIoSupport.rejectSymbolicLinks(storageRoot, directory);
        validateDirectoryEntries(directory, Set.of(METADATA_DIRECTORY, MULTIPART_DIRECTORY, TEMPORARY_DIRECTORY, SNAPSHOT_DIRECTORY));
        validateDirectoryEntries(directory.resolve(METADATA_DIRECTORY), Set.of(OBJECTS_DIRECTORY, BUCKETS_DIRECTORY));
    }

    private static void validateDirectoryEntries(Path directory, Set<String> allowedNames) {
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (!allowedNames.contains(entry.getFileName().toString()) || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Custom internal-directory contains unexpected provider layout entry: " + entry);
                }
            }
        } catch (NoSuchFileException _) {
            // No provider state has been created yet.
        } catch (IOException e) {
            throw new ObjectStorageException("Cannot validate custom internal-directory: " + directory, e);
        }
    }

    Path storageRoot() {
        return storageRoot;
    }

    Path configuredBucketPath() {
        return bucketPath.toAbsolutePath().normalize();
    }

    Path requireBucketRoot(String component) {
        if (!hasBucketParent) {
            throw new IllegalArgumentException("Local storage " + component + " requires a bucket path with a parent directory");
        }
        return storageRoot;
    }

    Path objectPath(String key) {
        Path path = LocalStorageIoSupport.resolveSafe(bucketPath, key);
        rejectInternalName(bucketPath.normalize().relativize(path).getName(0).toString());
        return path;
    }

    Path bucketPath(String name) {
        rejectInternalName(name);
        return LocalStorageIoSupport.resolveBucketPath(requireBucketRoot("bucket operations"), name);
    }

    Path rootInternalDirectory() {
        return storageRoot.resolve(internalDirectory);
    }

    Path objectMetadataRoot() {
        return rootInternalDirectory()
            .resolve(METADATA_DIRECTORY)
            .resolve(OBJECTS_DIRECTORY);
    }

    Path objectMetadataBucketRoot() {
        return objectMetadataRoot().resolve(bucketName);
    }

    Path objectMetadataBucketRoot(String name) {
        return providerManagedBucketDirectory(name, objectMetadataRoot(), "object metadata cleanup");
    }

    Path objectMetadataFile(String key) {
        Path metadataBucketRoot = objectMetadataBucketRoot();
        LocalStorageIoSupport.rejectSymbolicLinks(storageRoot, metadataBucketRoot);
        Path path = LocalStorageIoSupport.resolveSafe(metadataBucketRoot, key);
        rejectInternalName(metadataBucketRoot.relativize(path).getName(0).toString());
        return path;
    }

    List<Path> objectMetadataReadPaths(String key) {
        return List.of(
            objectMetadataFile(key),
            legacyObjectMetadataFile(key)
        );
    }

    List<Path> objectMetadataDeletePaths(String key) {
        return objectMetadataReadPaths(key);
    }

    Path bucketMetadataRoot() {
        return rootInternalDirectory()
            .resolve(METADATA_DIRECTORY)
            .resolve(BUCKETS_DIRECTORY);
    }

    Path bucketMetadataFile(String name) {
        rejectInternalName(name);
        LocalStorageIoSupport.resolveBucketPath(requireBucketRoot("bucket metadata operations"), name);
        Path metadataRoot = bucketMetadataRoot();
        LocalStorageIoSupport.rejectSymbolicLinks(storageRoot, metadataRoot);
        return LocalStorageIoSupport.resolveSafe(metadataRoot, name);
    }

    Path snapshotBucketDirectory() {
        return rootInternalDirectory()
            .resolve(SNAPSHOT_DIRECTORY)
            .resolve(bucketName);
    }

    Path snapshotBucketDirectory(String name) {
        return providerManagedBucketDirectory(
            name,
            rootInternalDirectory().resolve(SNAPSHOT_DIRECTORY),
            "snapshot cleanup"
        );
    }

    Path multipartBucketDirectory() {
        Path multipartBucketDirectory = rootInternalDirectory()
            .resolve(MULTIPART_DIRECTORY)
            .resolve(bucketName);
        LocalStorageIoSupport.rejectSymbolicLinks(storageRoot, multipartBucketDirectory);
        return multipartBucketDirectory;
    }

    Path multipartBucketDirectory(String name) {
        return providerManagedBucketDirectory(
            name,
            rootInternalDirectory().resolve(MULTIPART_DIRECTORY),
            "multipart upload cleanup"
        );
    }

    List<Path> multipartCleanupDirectories() {
        Path multipartBucketDirectory = multipartBucketDirectory();
        // The shared internal root can be in use by another operation even while it is empty.
        return List.of(
            multipartBucketDirectory,
            multipartBucketDirectory.getParent()
        );
    }

    Path temporaryBucketDirectory() {
        return rootInternalDirectory()
            .resolve(TEMPORARY_DIRECTORY)
            .resolve(bucketName);
    }

    Path temporaryBucketDirectory(String name) {
        rejectInternalName(name);
        LocalStorageIoSupport.resolveBucketPath(requireBucketRoot("temporary file cleanup"), name);
        return rootInternalDirectory()
            .resolve(TEMPORARY_DIRECTORY)
            .resolve(name);
    }

    Path objectTemporaryDirectory() {
        return temporaryBucketDirectory().resolve(OBJECTS_DIRECTORY);
    }

    Path objectMetadataTemporaryDirectory() {
        return temporaryBucketDirectory().resolve(OBJECT_METADATA_TEMPORARY_DIRECTORY);
    }

    Path bucketMetadataTemporaryDirectory(String name) {
        return temporaryBucketDirectory(name).resolve(BUCKET_METADATA_TEMPORARY_DIRECTORY);
    }

    List<Path> providerManagedBucketDirectories(String name) {
        return List.of(
            objectMetadataBucketRoot(name),
            multipartBucketDirectory(name),
            snapshotBucketDirectory(name),
            temporaryBucketDirectory(name)
        );
    }

    private Path legacyObjectMetadataFile(String key) {
        Path metadataRoot = bucketPath.resolve(LEGACY_METADATA_DIRECTORY);
        LocalStorageIoSupport.rejectSymbolicLinks(bucketPath.normalize(), metadataRoot.normalize());
        return LocalStorageIoSupport.resolveSafe(metadataRoot, key);
    }

    private Path providerManagedBucketDirectory(String name, Path root, String component) {
        rejectInternalName(name);
        requireBucketRoot(component);
        Path bucketDirectory = LocalStorageIoSupport.resolveBucketPath(root, name);
        LocalStorageIoSupport.rejectSymbolicLinks(storageRoot, bucketDirectory);
        return bucketDirectory;
    }

    static Optional<String> reservedLocalStorageNamespace(String name) {
        if (INTERNAL_DIRECTORY.equalsIgnoreCase(name)) {
            return Optional.of(INTERNAL_DIRECTORY);
        }
        if (LEGACY_METADATA_DIRECTORY.equalsIgnoreCase(name)) {
            return Optional.of(LEGACY_METADATA_DIRECTORY);
        }
        return Optional.empty();
    }

    private void rejectInternalName(String name) {
        if (internalDirectory.equalsIgnoreCase(name) && reservedLocalStorageNamespace(name).isEmpty()) {
            throw new IllegalArgumentException("Name uses the reserved " + internalDirectory + " namespace: " + name);
        }
    }

    boolean isInternalKey(String key) {
        String firstSegment = key.split("/", 2)[0];
        return internalDirectory.equalsIgnoreCase(firstSegment) || isReservedLocalStorageKey(key);
    }

    static boolean isReservedLocalStorageKey(String key) {
        int separator = key.indexOf('/');
        String firstSegment = separator >= 0 ? key.substring(0, separator) : key;
        return reservedLocalStorageNamespace(firstSegment).isPresent();
    }
}
