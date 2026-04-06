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
import org.jspecify.annotations.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
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

    public static final String METADATA_DIRECTORY = ".metadata";
    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );
    private static final FileAttribute<Set<PosixFilePermission>> DIRECTORY_PERMISSIONS_ATTRIBUTE =
        PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS);
    private static final FileAttribute<Set<PosixFilePermission>> FILE_PERMISSIONS_ATTRIBUTE =
        PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS);

    private final LocalStorageConfiguration configuration;
    private final Path metadataPath;

    public LocalStorageOperations(@Parameter LocalStorageConfiguration configuration) {
        this.configuration = configuration;
        this.metadataPath = configuration.getPath().resolve(METADATA_DIRECTORY);
        boolean metadataDirectoryCreated = mkdirs(metadataPath);
        if (!metadataDirectoryCreated) {
            throw new ObjectStorageException("Error creating metadata directory: " + metadataPath);
        }
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
        Path file = storeFile(request);
        storeMetadata(request);
        LocalStorageFile localFile = new LocalStorageFile(file);
        requestConsumer.accept(localFile);
        return UploadResponse.of(request.getKey(), UUID.randomUUID().toString(), localFile);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<LocalStorageEntry> retrieve(@NonNull String key) {
        Optional<Path> file = retrieveFile(key);
        return file.map(path -> new LocalStorageEntry(key, path, retrieveMetadata(key)));
    }

    @Override
    @NonNull
    public LocalStorageFile delete(@NonNull String key) {
        Optional<Path> file = retrieveFile(key);
        deleteFile(key);
        deleteMetadata(key);
        return new LocalStorageFile(file.orElse(null));
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
                if (key.startsWith(METADATA_DIRECTORY)) {
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
        retrieveFile(sourceKey).ifPresent(source -> {
            try (InputStream in = Files.newInputStream(source)) {
                storeFile(destinationKey, in);
                storeMetadata(destinationKey, retrieveMetadata(sourceKey));
            } catch (IOException e) {
                throw new ObjectStorageException("Error copying file: " + source, e);
            }
        });
    }

    private Optional<Path> retrieveFile(String key) {
        Path file = resolveSafe(configuration.getPath(), key);
        if (Files.exists(file)) {
            return Optional.of(file);
        } else {
            return Optional.empty();
        }
    }

    private Map<String, String> retrieveMetadata(String key) {
        Properties metadataProperties = new Properties();
        Path metadata = resolveSafe(metadataPath, key).normalize();
        if (Files.exists(metadata)) {
            try (InputStream metadataIn = Files.newInputStream(metadata)) {
                metadataProperties.load(metadataIn);
            } catch (IOException e) {
                //no op
            }
        }
        Map<String, String> result = new HashMap<>(metadataProperties.size());
        for (final String name: metadataProperties.stringPropertyNames()) {
            result.put(name, metadataProperties.getProperty(name));
        }
        return result;
    }

    private void deleteFile(String key) {
        retrieveFile(key).ifPresent(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new ObjectStorageException("Error deleting file: " + path, e);
            }
        });
    }

    private void deleteMetadata(String key) {
        Path metadata = resolveSafe(metadataPath, key);
        if (Files.exists(metadata)) {
            try {
                Files.delete(metadata);
            } catch (IOException e) {
                //no op
            }
        }
    }

    private Path storeFile(UploadRequest request) {
        return storeFile(request.getKey(), request.getInputStream());
    }

    private Path storeFile(String key, InputStream inputStream) {
        Path file = resolveSafe(configuration.getPath(), key);
        mkdirs(file.getParent());
        try (OutputStream fileOut = newOutputStream(file)) {
            inputStream.transferTo(fileOut);
            return file;
        } catch (IOException e) {
            throw new ObjectStorageException("Error copying file to: " + file, e);
        }
    }

    private void storeMetadata(UploadRequest request) {
        storeMetadata(request.getKey(), request.getMetadata());
    }

    private void storeMetadata(String key, Map<String, String> metadata) {
        Properties metadataProperties = new Properties();
        metadataProperties.putAll(metadata);
        Path metadataFilePath = resolveSafe(metadataPath, key);
        mkdirs(metadataFilePath.getParent());
        try (OutputStream metadataOut = newOutputStream(metadataFilePath)) {
            metadataProperties.store(metadataOut, "Metadata for file: " + key);
        } catch (IOException e) {
            //no op
        }
    }

    private boolean mkdirs(Path path) {
        try {
            if (supportsPosixPermissions(path)) {
                Files.createDirectories(path, DIRECTORY_PERMISSIONS_ATTRIBUTE);
            } else {
                Files.createDirectories(path);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private OutputStream newOutputStream(Path file) throws IOException {
        if (supportsPosixPermissions(file)) {
            if (Files.notExists(file)) {
                Files.createFile(file, FILE_PERMISSIONS_ATTRIBUTE);
            } else {
                Files.setPosixFilePermissions(file, FILE_PERMISSIONS);
            }
            return Files.newOutputStream(file, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        return Files.newOutputStream(file);
    }

    private boolean supportsPosixPermissions(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static Path resolveSafe(Path parent, String key) {
        Path file = parent.resolve(key).normalize();
        if (!file.startsWith(parent)) {
            throw new IllegalArgumentException("Path lies outside the configured bucket");
        }
        return file;
    }

    /**
     * A simple wrapper around a path.
     * @param path Where on disk the local storage provider has stored the actual data.
     */
    public record LocalStorageFile(Path path) { }
}
