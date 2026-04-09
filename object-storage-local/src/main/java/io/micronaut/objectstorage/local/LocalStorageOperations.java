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
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final LocalStorageConfiguration configuration;
    private final LocalStorageObjectMetadataOperations objectMetadataOperations;
    private final boolean supportsPosixPermissions;

    public LocalStorageOperations(@Parameter LocalStorageConfiguration configuration,
                                  LocalStorageObjectMetadataOperations objectMetadataOperations) {
        this.configuration = configuration;
        this.objectMetadataOperations = objectMetadataOperations;
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
        Path file = LocalStorageIoSupport.resolveSafe(configuration.getPath(), request.getKey());
        objectMetadataOperations.prepareMetadataTarget(request.getKey());
        storeFile(file, request.getInputStream());
        objectMetadataOperations.save(new ObjectMetadataWrite(
            request.getKey(),
            request.getMetadata(),
            Map.of(),
            request.getContentType().orElse(null),
            request.getContentSize().orElse(null),
            null,
            null
        ));
        LocalStorageFile localFile = new LocalStorageFile(file);
        requestConsumer.accept(localFile);
        return UploadResponse.of(request.getKey(), UUID.randomUUID().toString(), localFile);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<LocalStorageEntry> retrieve(@NonNull String key) {
        Optional<Path> file = retrieveFile(key);
        return file.map(path -> new LocalStorageEntry(
            key,
            path,
            objectMetadataOperations.retrieve(key).map(ObjectMetadataEntry::metadata).orElse(Collections.emptyMap())
        ));
    }

    @Override
    @NonNull
    public LocalStorageFile delete(@NonNull String key) {
        Optional<Path> file = retrieveFile(key);
        deleteFile(key);
        objectMetadataOperations.delete(key);
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
            Path destinationFile = LocalStorageIoSupport.resolveSafe(configuration.getPath(), destinationKey);
            objectMetadataOperations.prepareMetadataTarget(destinationKey);
            try (InputStream in = newInputStreamNoFollow(source)) {
                storeFile(destinationFile, in);
                objectMetadataOperations.retrieve(sourceKey).ifPresentOrElse(entry ->
                    objectMetadataOperations.save(new ObjectMetadataWrite(
                        destinationKey,
                        entry.metadata(),
                        entry.attributes(),
                        entry.contentType(),
                        entry.contentLength(),
                        entry.etag(),
                        entry.lastModified()
                    )),
                    () -> objectMetadataOperations.delete(destinationKey)
                );
            } catch (IOException e) {
                throw new ObjectStorageException("Error copying file: " + source, e);
            }
        });
    }

    private Optional<Path> retrieveFile(String key) {
        Path file = LocalStorageIoSupport.resolveSafe(configuration.getPath(), key);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.of(file);
        } else {
            return Optional.empty();
        }
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

    private Path storeFile(Path file, InputStream inputStream) {
        mkdirs(file.getParent());
        try (OutputStream fileOut = newOutputStreamNoFollow(file)) {
            inputStream.transferTo(fileOut);
            return file;
        } catch (IOException e) {
            throw new ObjectStorageException("Error copying file to: " + file, e);
        }
    }

    private boolean mkdirs(Path path) {
        return LocalStorageIoSupport.mkdirs(configuration.getPath(), path, supportsPosixPermissions);
    }

    private OutputStream newOutputStreamNoFollow(Path file) throws IOException {
        return LocalStorageIoSupport.newOutputStreamNoFollow(file, supportsPosixPermissions);
    }

    private static InputStream newInputStreamNoFollow(Path path) throws IOException {
        return LocalStorageIoSupport.newInputStreamNoFollow(path);
    }

    /**
     * A simple wrapper around a path.
     * @param path Where on disk the local storage provider has stored the actual data.
     */
    public record LocalStorageFile(Path path) { }
}
