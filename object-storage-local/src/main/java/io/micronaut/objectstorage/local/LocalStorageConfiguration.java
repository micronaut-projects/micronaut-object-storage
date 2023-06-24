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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.micronaut.objectstorage.local.LocalStorageConfiguration.PREFIX;

/**
 * Configuration class for local storage.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@EachProperty(PREFIX)
public class LocalStorageConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "local";

    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    @NonNull
    private Path path;

    public LocalStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return The path of the local storage.
     */
    @NonNull
    public Path getPath() {
        if (path == null) {
            try {
                path = Files.createTempDirectory("micronaut-object-storage-local");
            } catch (Exception e) {
                throw new ObjectStorageException("Error creating temp directory for local storage", e);
            }
        }
        return path;
    }

    /**
     * @param path The path of the local storage.
     */
    public void setPath(@NonNull Path path) {
        this.path = path;
    }
}
