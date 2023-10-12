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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
        try (Stream<Path> stream = Files.find(configuration.getPath(), Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile())) {
            return stream
            .map(p -> configuration.getPath().relativize(p))
            .map(Path::toString)
            .filter(s -> !s.startsWith(METADATA_DIRECTORY))
            .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new ObjectStorageException("Error listing objects", e);
        }
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
        try (OutputStream fileOut = Files.newOutputStream(file)) {
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
        try (OutputStream metadataOut = new FileOutputStream(metadataFilePath.toFile())) {
            metadataProperties.store(metadataOut, "Metadata for file: " + key);
        } catch (IOException e) {
            //no op
        }
    }

    private boolean mkdirs(Path path) {
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Path resolveSafe(Path parent, String key) {
        Path file = parent.resolve(key).normalize();
        if (!file.startsWith(parent)) {
            throw new IllegalArgumentException("Path lies outside the configured bucket");
        }
        return file;
    }

    record LocalStorageFile(Path path) { }
}
