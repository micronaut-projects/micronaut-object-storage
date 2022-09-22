/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Object storage entry.
 *
 * @author Pavol Gressa
 * @since 1.0
 * @param <O> Cloud vendor-specific response object.
 */
public interface ObjectStorageEntry<O> {

    /**
     * The object path on object storage. For example {@code /path/to}
     *
     * @return object path or empty string if the object is placed at the root of bucket
     */
    @NonNull
    String getKey();

    /**
     * @return The object content.
     */
    @NonNull
    InputStream getInputStream();

    /**
     * @return The underlying cloud vendor-specific response object.
     */
    @NonNull
    O getNativeEntry();

    /**
     * @return a map with key-value pairs that were stored along the file. An empty map by default.
     * @since 1.1.0
     */
    @NonNull
    default Map<String, String> getMetadata() {
        return Collections.emptyMap();
    }

    /**
     * @return the MIME type of the entry.
     * @since 1.1.0
     */
    @NonNull
    default Optional<String> getContentType() {
        return Optional.empty();
    }

    /**
     * @return a {@link StreamedFile} from this entry.
     * @since 1.1.0
     */
    @NonNull
    default StreamedFile toStreamedFile() {
        MediaType mediaType = MediaType.of(getContentType().orElse(MediaType.ALL));
        String key = getKey();
        String fileName = key.substring(key.lastIndexOf(File.separator) + 1);
        return new StreamedFile(getInputStream(), mediaType).attach(fileName);
    }

    /**
     * @return a {@link SystemFile} from this entry. Note that calling this method will consume the
     * {@link #getInputStream()}, which will be closed.
     * @since 1.1.0
     */
    @NonNull
    default SystemFile toSystemFile() {
        try {
            String key = getKey();
            String fileName = key.substring(key.lastIndexOf(File.separator) + 1);
            File file = Files.createTempFile("", fileName).toFile();
            file.deleteOnExit();
            Files.copy(getInputStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getInputStream().close();
            MediaType mediaType = MediaType.of(getContentType().orElse(MediaType.ALL));
            return new SystemFile(file, mediaType).attach(fileName);
        } catch (IOException e) {
            throw new ObjectStorageException(e);
        }
    }
}
