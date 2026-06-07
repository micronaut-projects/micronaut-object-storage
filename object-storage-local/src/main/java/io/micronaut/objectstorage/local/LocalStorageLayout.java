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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    LocalStorageLayout(LocalStorageConfiguration configuration) {
        this(configuration.getPath(), configuration.getName());
    }

    private LocalStorageLayout(Path bucketPath, String configurationName) {
        this.bucketPath = bucketPath;
        Path normalizedBucketPath = bucketPath.toAbsolutePath().normalize();
        Path bucketParent = normalizedBucketPath.getParent();
        this.hasBucketParent = bucketParent != null;
        this.storageRoot = hasBucketParent ? bucketParent : normalizedBucketPath;
        Path bucketFileName = normalizedBucketPath.getFileName();
        String reservedNamespace = bucketFileName == null ? null : reservedLocalStorageNamespace(bucketFileName.toString()).orElse(null);
        if (reservedNamespace != null) {
            throw new IllegalArgumentException("Local storage bucket path uses the reserved " + reservedNamespace + " namespace: " + bucketPath);
        }
        this.bucketName = bucketFileName == null ? configurationName : bucketFileName.toString();
    }

    Path storageRoot() {
        return storageRoot;
    }

    Path requireBucketRoot(String component) {
        if (!hasBucketParent) {
            throw new IllegalArgumentException("Local storage " + component + " requires a bucket path with a parent directory");
        }
        return storageRoot;
    }

    Path objectPath(String key) {
        return LocalStorageIoSupport.resolveSafe(bucketPath, key);
    }

    Path bucketPath(String name) {
        return LocalStorageIoSupport.resolveBucketPath(requireBucketRoot("bucket operations"), name);
    }

    Path rootInternalDirectory() {
        return storageRoot.resolve(INTERNAL_DIRECTORY);
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
        return LocalStorageIoSupport.resolveSafe(metadataBucketRoot, key);
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
        return List.of(
            multipartBucketDirectory,
            multipartBucketDirectory.getParent(),
            rootInternalDirectory()
        );
    }

    Path temporaryBucketDirectory() {
        return rootInternalDirectory()
            .resolve(TEMPORARY_DIRECTORY)
            .resolve(bucketName);
    }

    Path temporaryBucketDirectory(String name) {
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

    List<Path> snapshotCleanupDirectories() {
        Path snapshotBucketDirectory = snapshotBucketDirectory();
        return List.of(
            snapshotBucketDirectory,
            snapshotBucketDirectory.getParent(),
            rootInternalDirectory()
        );
    }

    private Path legacyObjectMetadataFile(String key) {
        Path metadataRoot = bucketPath.resolve(LEGACY_METADATA_DIRECTORY);
        LocalStorageIoSupport.rejectSymbolicLinks(bucketPath.normalize(), metadataRoot.normalize());
        return LocalStorageIoSupport.resolveSafe(metadataRoot, key);
    }

    private Path providerManagedBucketDirectory(String name, Path root, String component) {
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

    static boolean isReservedLocalStorageKey(String key) {
        int separator = key.indexOf('/');
        String firstSegment = separator >= 0 ? key.substring(0, separator) : key;
        return reservedLocalStorageNamespace(firstSegment).isPresent();
    }
}
