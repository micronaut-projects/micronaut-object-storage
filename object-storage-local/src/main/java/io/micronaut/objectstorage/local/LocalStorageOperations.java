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
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry;
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * An implementation of {@link ObjectStorageOperations} that uses the local file system. Useful for
 * testing.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@EachBean(LocalStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = LocalStorageConfiguration.class)
@Primary
public class LocalStorageOperations implements ObjectStorageOperations<
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile> {

    /**
     * Legacy local metadata sidecar directory.
     */
    public static final String METADATA_DIRECTORY = LocalStorageLayout.LEGACY_METADATA_DIRECTORY;
    static final String INTERNAL_DIRECTORY = LocalStorageLayout.INTERNAL_DIRECTORY;
    static final String LEGACY_METADATA_DIRECTORY = LocalStorageLayout.LEGACY_METADATA_DIRECTORY;
    static final String OBJECTS_DIRECTORY = LocalStorageLayout.OBJECTS_DIRECTORY;
    static final String MULTIPART_DIRECTORY = LocalStorageLayout.MULTIPART_DIRECTORY;
    static final String SNAPSHOT_DIRECTORY = LocalStorageLayout.SNAPSHOT_DIRECTORY;
    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;
    private static final String TEMPORARY_FILE_PREFIX = "micronaut-object-storage-local";
    private static final String TEMPORARY_FILE_SUFFIX = ".tmp";

    private final LocalStorageConfiguration configuration;
    private final LocalStorageLayout layout;
    private final LocalStorageMetadataMode metadataMode;
    private final ObjectMetadataOperations<Path> objectMetadataOperations;
    private final LocalStorageObjectMetadataOperations sidecarMetadataOperations;
    private final boolean supportsPosixPermissions;

    /**
     * Create local storage operations.
     *
     * @param configuration The local storage configuration.
     * @deprecated Use {@link #LocalStorageOperations(LocalStorageConfiguration, ObjectMetadataOperations)} instead.
     */
    @Deprecated(since = "3.0.0")
    public LocalStorageOperations(@Parameter LocalStorageConfiguration configuration) {
        this(configuration, new LocalStorageObjectMetadataOperations(configuration));
    }

    /**
     * Create local storage operations.
     *
     * @param configuration The local storage configuration.
     * @param objectMetadataOperations The local object metadata operations.
     * @deprecated Use {@link #LocalStorageOperations(LocalStorageConfiguration, ObjectMetadataOperations)} instead.
     */
    @Deprecated(since = "3.0.0")
    public LocalStorageOperations(@Parameter LocalStorageConfiguration configuration,
                                  LocalStorageObjectMetadataOperations objectMetadataOperations) {
        this(configuration, (ObjectMetadataOperations<Path>) objectMetadataOperations);
    }

    /**
     * Create local storage operations with a custom metadata persistence implementation.
     *
     * @param configuration The local storage configuration.
     * @param objectMetadataOperations The object metadata operations.
     */
    @Inject
    public LocalStorageOperations(@Parameter LocalStorageConfiguration configuration,
                                  ObjectMetadataOperations<Path> objectMetadataOperations) {
        this.configuration = configuration;
        this.layout = new LocalStorageLayout(configuration);
        this.metadataMode = configuration.getMetadataMode();
        this.objectMetadataOperations = objectMetadataOperations;
        this.sidecarMetadataOperations = objectMetadataOperations instanceof LocalStorageObjectMetadataOperations localOperations
            ? localOperations
            : new LocalStorageObjectMetadataOperations(configuration);
        this.supportsPosixPermissions = configuration.getPath().getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @Override
    @NonNull
    public UploadResponse<LocalStorageFile> upload(@NonNull UploadRequest request) {
        return upload(request, localStorageFile -> { });
    }

    @Override
    @NonNull
    public UploadResponse<LocalStorageFile> upload(@NonNull UploadRequest request,
                                                   @NonNull Consumer<LocalStorageFile> requestConsumer) {
        validateMetadata(metadataMode, request.getMetadata());
        String key = request.getKey();
        Path file = layout.objectPath(key);
        Lock bucketLock = LocalStorageLocks.bucketReadLock(layout.configuredBucketPath());
        ReentrantLock mutationLock = LocalStorageLocks.objectMutationLock(file);
        LocalStorageFile localFile;
        String eTag;
        bucketLock.lock();
        try {
            mutationLock.lock();
            try {
                StoredFileSnapshot snapshot = snapshotStoredFile(file);
                RuntimeException failure = null;
                boolean preserveSnapshot = false;
                try {
                    prepareMetadataForMutation(key);
                    eTag = storeFile(file, request.getInputStream());
                    if (metadataMode == LocalStorageMetadataMode.ENABLED) {
                        objectMetadataOperations.save(new ObjectMetadataWrite(
                            key,
                            request.getMetadata(),
                            Map.of(),
                            request.getContentType().orElse(null),
                            request.getContentSize().orElse(null),
                            eTag,
                            null
                        ));
                    }
                } catch (RuntimeException e) {
                    failure = e;
                    boolean restored = restoreStoredFileAfterFailure(file, snapshot, e);
                    preserveSnapshot = snapshot.exists() && !restored;
                    throw e;
                } finally {
                    deleteSnapshot(snapshot, failure, preserveSnapshot);
                }
                localFile = new LocalStorageFile(file);
            } finally {
                mutationLock.unlock();
            }
        } finally {
            bucketLock.unlock();
        }
        requestConsumer.accept(localFile);
        return UploadResponse.of(key, eTag, localFile);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<LocalStorageEntry> retrieve(@NonNull String key) {
        Optional<Path> file = retrieveFile(key);
        return file.map(path -> new LocalStorageEntry(
            key,
            path,
            retrieveMetadata(key)
        ));
    }

    @Override
    @NonNull
    public LocalStorageFile delete(@NonNull String key) {
        Path path = layout.objectPath(key);
        Lock bucketLock = LocalStorageLocks.bucketReadLock(layout.configuredBucketPath());
        ReentrantLock mutationLock = LocalStorageLocks.objectMutationLock(path);
        bucketLock.lock();
        try {
            mutationLock.lock();
            try {
                prepareMetadataForMutation(key);
                StoredFileSnapshot snapshot = snapshotStoredFile(path);
                if (!snapshot.exists()) {
                    return new LocalStorageFile(null);
                }
                RuntimeException failure = null;
                boolean preserveSnapshot = false;
                try {
                    deleteFile(path);
                    if (metadataMode == LocalStorageMetadataMode.ENABLED) {
                        objectMetadataOperations.delete(key);
                    }
                } catch (RuntimeException e) {
                    failure = e;
                    boolean restored = restoreStoredFileAfterFailure(path, snapshot, e);
                    preserveSnapshot = snapshot.exists() && !restored;
                    throw e;
                } finally {
                    deleteSnapshot(snapshot, failure, preserveSnapshot);
                }
            } finally {
                mutationLock.unlock();
            }
        } finally {
            bucketLock.unlock();
        }
        pruneEmptyParentDirectories(path);
        return new LocalStorageFile(path);
    }

    @Override
    public boolean exists(@NonNull String key) {
        return retrieveFile(key).isPresent();
    }

    @Override
    @NonNull
    public Set<String> listObjects() {
        Set<String> keys = new LinkedHashSet<>();
        String continuationToken = null;
        do {
            ListObjectsResponse response = listObjects(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken));
            keys.addAll(response.getKeys());
            continuationToken = response.getContinuationToken().orElse(null);
        } while (continuationToken != null);
        return keys;
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        String prefix = request.getPrefix().orElse(null);
        String continuationToken = request.getContinuationToken().orElse(null);
        int pageSize = request.getPageSize();
        TreeSet<String> candidates = new TreeSet<>();
        String[] maximumMatchingKey = new String[1];
        final int maxCandidates = (pageSize == Integer.MAX_VALUE) ? Integer.MAX_VALUE : pageSize + 1;
        try (Stream<Path> stream = Files.find(configuration.getPath(), Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile())) {
            stream.forEach(path -> {
                Path relativePath = configuration.getPath().relativize(path);
                String key = relativePath.toString();
                if (File.separatorChar != '/') {
                    key = key.replace(File.separatorChar, '/');
                }
                if (isReservedLocalStorageKey(key)) {
                    return;
                }
                if (prefix != null && !key.startsWith(prefix)) {
                    return;
                }
                if (maximumMatchingKey[0] == null || key.compareTo(maximumMatchingKey[0]) > 0) {
                    maximumMatchingKey[0] = key;
                }
                if (continuationToken != null && key.compareTo(continuationToken) <= 0) {
                    return;
                }
                candidates.add(key);
                if (candidates.size() > maxCandidates) {
                    candidates.pollLast();
                }
            });
        } catch (IOException e) {
            throw new ObjectStorageException("Error listing objects", e);
        }

        if (continuationToken != null && maximumMatchingKey[0] != null && maximumMatchingKey[0].compareTo(continuationToken) <= 0) {
            return new ListObjectsResponse(Collections.emptyList());
        }

        if (candidates.isEmpty()) {
            return new ListObjectsResponse(Collections.emptyList());
        }

        List<String> page = new ArrayList<>(Math.min(pageSize, candidates.size()));
        String nextContinuationToken = null;
        int index = 0;
        for (String key : candidates) {
            if (index < pageSize) {
                page.add(key);
            } else {
                nextContinuationToken = page.get(page.size() - 1);
                break;
            }
            index++;
        }
        return new ListObjectsResponse(page, nextContinuationToken);
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        Path sourceFile = layout.objectPath(sourceKey);
        Path destinationFile = layout.objectPath(destinationKey);
        Lock bucketLock = LocalStorageLocks.bucketReadLock(layout.configuredBucketPath());
        bucketLock.lock();
        try {
            if (sourceFile.equals(destinationFile)) {
                return;
            }
            int sourceLockIndex = LocalStorageLocks.objectMutationLockIndex(sourceFile);
            int destinationLockIndex = LocalStorageLocks.objectMutationLockIndex(destinationFile);
            ReentrantLock sourceLock = LocalStorageLocks.objectMutationLock(sourceLockIndex);
            ReentrantLock destinationLock = LocalStorageLocks.objectMutationLock(destinationLockIndex);
            if (sourceLock == destinationLock) {
                sourceLock.lock();
                try {
                    retrieveFile(sourceKey).ifPresent(source -> copyStoredFile(sourceKey, source, destinationKey, destinationFile));
                } finally {
                    sourceLock.unlock();
                }
                return;
            }
            ReentrantLock firstLock;
            ReentrantLock secondLock;
            if (sourceLockIndex < destinationLockIndex) {
                firstLock = sourceLock;
                secondLock = destinationLock;
            } else {
                firstLock = destinationLock;
                secondLock = sourceLock;
            }
            firstLock.lock();
            try {
                secondLock.lock();
                try {
                    retrieveFile(sourceKey).ifPresent(source -> copyStoredFile(sourceKey, source, destinationKey, destinationFile));
                } finally {
                    secondLock.unlock();
                }
            } finally {
                firstLock.unlock();
            }
        } finally {
            bucketLock.unlock();
        }
    }

    private Optional<Path> retrieveFile(String key) {
        Path file = layout.objectPath(key);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.of(file);
        } else {
            return Optional.empty();
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new ObjectStorageException("Error deleting file: " + path, e);
        }
    }

    private void pruneEmptyParentDirectories(Path deletedPath) {
        Path boundary = layout.configuredBucketPath();
        Lock bucketLock = LocalStorageLocks.bucketWriteLock(boundary);
        bucketLock.lock();
        try {
            Path directory = deletedPath.toAbsolutePath().normalize().getParent();
            while (directory != null && directory.startsWith(boundary) && !directory.equals(boundary)) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    return;
                }
                try {
                    Files.delete(directory);
                } catch (IOException e) {
                    // A non-empty or otherwise unavailable directory ends this best-effort cleanup.
                    return;
                }
                directory = directory.getParent();
            }
        } finally {
            bucketLock.unlock();
        }
    }

    private void copyStoredFile(String sourceKey, Path source, String destinationKey, Path destinationFile) {
        prepareMetadataForMutation(destinationKey);
        StoredFileSnapshot snapshot = snapshotStoredFile(destinationFile);
        RuntimeException failure = null;
        boolean preserveSnapshot = false;
        try (InputStream in = newInputStreamNoFollow(source)) {
            String eTag = storeFile(destinationFile, in);
            if (metadataMode == LocalStorageMetadataMode.ENABLED) {
                copyObjectMetadata(sourceKey, destinationKey, eTag);
            }
        } catch (IOException e) {
            ObjectStorageException objectStorageException = new ObjectStorageException("Error copying file: " + source, e);
            failure = objectStorageException;
            boolean restored = restoreStoredFileAfterFailure(destinationFile, snapshot, objectStorageException);
            preserveSnapshot = snapshot.exists() && !restored;
            throw objectStorageException;
        } catch (RuntimeException e) {
            failure = e;
            boolean restored = restoreStoredFileAfterFailure(destinationFile, snapshot, e);
            preserveSnapshot = snapshot.exists() && !restored;
            throw e;
        } finally {
            deleteSnapshot(snapshot, failure, preserveSnapshot);
        }
    }

    private void copyObjectMetadata(String sourceKey, String destinationKey, String eTag) {
        objectMetadataOperations.retrieve(sourceKey).ifPresentOrElse(entry ->
            objectMetadataOperations.save(new ObjectMetadataWrite(
                destinationKey,
                entry.metadata(),
                entry.attributes(),
                entry.contentType(),
                entry.contentLength(),
                eTag,
                entry.lastModified()
            )),
            () -> objectMetadataOperations.save(new ObjectMetadataWrite(
                destinationKey,
                Map.of(),
                Map.of(),
                null,
                null,
                eTag,
                null
            ))
        );
    }

    private Map<String, String> retrieveMetadata(String key) {
        if (metadataMode == LocalStorageMetadataMode.NONE) {
            return Collections.emptyMap();
        }
        return objectMetadataOperations.retrieve(key)
            .map(ObjectMetadataEntry::metadata)
            .orElse(Collections.emptyMap());
    }

    private void prepareMetadataForMutation(String key) {
        if (metadataMode == LocalStorageMetadataMode.NONE) {
            // Remove only built-in local sidecars. A custom SPI bean must never be invoked in NONE mode.
            sidecarMetadataOperations.delete(key);
        }
    }

    static void validateMetadata(LocalStorageMetadataMode metadataMode, Map<String, String> metadata) {
        if (metadataMode == LocalStorageMetadataMode.NONE && !metadata.isEmpty()) {
            throw new ObjectStorageException("Local storage metadata mode NONE does not support user metadata");
        }
    }

    private StoredFileSnapshot snapshotStoredFile(Path file) {
        Path snapshotDirectory = layout.snapshotBucketDirectory();
        List<Path> cleanupDirectories = layout.snapshotCleanupDirectories();
        try {
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), snapshotDirectory);
            mkdirs(layout.rootInternalDirectory(), snapshotDirectory);
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), snapshotDirectory);
            Path snapshot = LocalStorageIoSupport.createTempFile(
                snapshotDirectory,
                TEMPORARY_FILE_PREFIX,
                ".snapshot",
                supportsPosixPermissions
            );
            return copyStoredFileToSnapshot(file, snapshot, cleanupDirectories);
        } catch (NoSuchFileException e) {
            if (isMissingStoredFile(file, e)) {
                deleteEmptySnapshotDirectories(cleanupDirectories, null);
                return StoredFileSnapshot.empty();
            }
            deleteEmptySnapshotDirectories(cleanupDirectories, e);
            throw new ObjectStorageException("Error snapshotting file before update: " + file, e);
        } catch (IOException e) {
            deleteEmptySnapshotDirectories(cleanupDirectories, e);
            throw new ObjectStorageException("Error snapshotting file before update: " + file, e);
        }
    }

    private static boolean isMissingStoredFile(Path file, NoSuchFileException e) {
        return file.toString().equals(e.getFile());
    }

    private StoredFileSnapshot copyStoredFileToSnapshot(Path file, Path snapshot, List<Path> cleanupDirectories) throws IOException {
        try (InputStream in = newInputStreamNoFollow(file);
             OutputStream out = Files.newOutputStream(snapshot)) {
            in.transferTo(out);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(snapshot);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        return new StoredFileSnapshot(snapshot, cleanupDirectories);
    }

    private boolean restoreStoredFileAfterFailure(Path file, StoredFileSnapshot snapshot, RuntimeException failure) {
        try {
            // Restore only the local file state; metadata-store transactionality belongs to the metadata implementation.
            if (snapshot.exists()) {
                moveSnapshotReplacing(snapshot.path(), file);
            } else {
                Files.deleteIfExists(file);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            failure.addSuppressed(e);
            return false;
        }
    }

    private void moveSnapshotReplacing(Path snapshot, Path file) throws IOException {
        LocalStorageIoSupport.moveReplacing(snapshot, file);
    }

    private void deleteSnapshot(StoredFileSnapshot snapshot, RuntimeException failure, boolean preserveSnapshot) {
        if (preserveSnapshot) {
            return;
        }
        if (snapshot.exists()) {
            try {
                Files.deleteIfExists(snapshot.path());
            } catch (IOException e) {
                recordSnapshotCleanupFailure(failure, e);
            }
        }
        deleteEmptySnapshotDirectories(snapshot.cleanupDirectories(), failure);
    }

    static Optional<String> reservedLocalStorageNamespace(String name) {
        return LocalStorageLayout.reservedLocalStorageNamespace(name);
    }

    private static boolean isReservedLocalStorageKey(String key) {
        return LocalStorageLayout.isReservedLocalStorageKey(key);
    }

    private void deleteEmptySnapshotDirectories(List<Path> directories, Throwable failure) {
        deleteEmptyDirectories(directories, failure);
    }

    private void deleteEmptyDirectories(List<Path> directories, Throwable failure) {
        for (Path directory : directories) {
            try {
                Files.deleteIfExists(directory);
            } catch (DirectoryNotEmptyException ignored) {
                // Another snapshot or provider-managed file still uses this directory.
            } catch (IOException e) {
                recordSnapshotCleanupFailure(failure, e);
            }
        }
    }

    private static void recordSnapshotCleanupFailure(Throwable failure, IOException cleanupFailure) {
        if (failure != null) {
            failure.addSuppressed(cleanupFailure);
        }
        // After a successful mutation, leftover snapshots are cleanup debt, not operation failure.
    }

    private String storeFile(Path file, InputStream inputStream) {
        mkdirs(configuration.getPath(), file.getParent());
        Path temporaryDirectory = layout.objectTemporaryDirectory();
        String[] eTag = new String[1];
        try (InputStream in = inputStream) {
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), temporaryDirectory);
            mkdirs(layout.rootInternalDirectory(), temporaryDirectory);
            LocalStorageIoSupport.rejectSymbolicLinks(layout.storageRoot(), temporaryDirectory);
            LocalStorageIoSupport.writeAndReplace(
                file,
                temporaryDirectory,
                TEMPORARY_FILE_PREFIX,
                TEMPORARY_FILE_SUFFIX,
                supportsPosixPermissions,
                out -> eTag[0] = LocalStorageETag.transferTo(in, out)
            );
            return eTag[0];
        } catch (IOException e) {
            throw new ObjectStorageException("Error copying file to: " + file, e);
        }
    }

    private boolean mkdirs(Path rootPath, Path path) {
        return LocalStorageIoSupport.mkdirs(rootPath, path, supportsPosixPermissions);
    }

    private static InputStream newInputStreamNoFollow(Path path) throws IOException {
        return LocalStorageIoSupport.newInputStreamNoFollow(path);
    }

    /**
     * A simple wrapper around a path.
     * @param path Where on disk the local storage provider has stored the actual data.
     */
    public record LocalStorageFile(Path path) { }

    private record StoredFileSnapshot(Path path, List<Path> cleanupDirectories) {
        static StoredFileSnapshot empty() {
            return new StoredFileSnapshot(null, List.of());
        }

        boolean exists() {
            return path != null;
        }
    }
}
