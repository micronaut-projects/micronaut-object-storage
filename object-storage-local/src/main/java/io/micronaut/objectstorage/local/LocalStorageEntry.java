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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link ObjectStorageEntry} implementation for local storage.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
public class LocalStorageEntry implements ObjectStorageEntry<Path> {

    @NonNull
    private final String key;

    @NonNull
    private final Path file;

    @NonNull
    private final Map<String, String> metadata;

    public LocalStorageEntry(@NonNull String key, @NonNull Path file, @NonNull Map<String, String> metadata) {
        this.key = key;
        this.file = file;
        this.metadata = metadata;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public InputStream getInputStream() {
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new ObjectStorageException("Error opening input stream for file: " + file, e);
        }
    }

    @Override
    public Path getNativeEntry() {
        return file;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(URLConnection.guessContentTypeFromName(file.toFile().getName()));
    }

}
