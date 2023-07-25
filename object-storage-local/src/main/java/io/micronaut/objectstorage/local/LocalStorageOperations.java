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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        boolean metadataDirectoryCreated = metadataPath.toFile().mkdirs();
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
        try (Stream<Path> stream = Files.list(configuration.getPath())) {
            return stream
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(string -> !string.equals(METADATA_DIRECTORY))
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
        Path file = Paths.get(configuration.getPath().toString(), key);
        if (Files.exists(file)) {
            return Optional.of(file);
        } else {
            return Optional.empty();
        }
    }

    private Map<String, String> retrieveMetadata(String key) {
        Properties metadataProperties = new Properties();
        Path metadata = Paths.get(metadataPath.toString(), key);
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
        Path metadata = Paths.get(metadataPath.toString(), key);
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
        File file = new File(configuration.getPath().toFile(), key);
        try (OutputStream fileOut = new FileOutputStream(file)) {
            inputStream.transferTo(fileOut);
            return file.toPath();
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
        Path metadataFilePath = Paths.get(metadataPath.toString(), key);
        try (OutputStream metadataOut = new FileOutputStream(metadataFilePath.toFile())) {
            metadataProperties.store(metadataOut, "Metadata for file: " + key);
        } catch (IOException e) {
            //no op
        }
    }

    record LocalStorageFile(Path path) { }
}
