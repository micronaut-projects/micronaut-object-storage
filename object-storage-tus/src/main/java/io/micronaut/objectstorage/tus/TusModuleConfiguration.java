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
package io.micronaut.objectstorage.tus;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * tus module configuration.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@ConfigurationProperties(TusModuleConfiguration.PREFIX)
public final class TusModuleConfiguration {

    public static final String PREFIX = "micronaut.object-storage.tus";

    private boolean enabled;
    private String basePath = "/tus";
    private long maxChunkSize = 16L * 1024L * 1024L;
    private Path storageDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "micronaut-object-storage-tus");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public long getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(long maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    public void setStorageDirectory(Path storageDirectory) {
        this.storageDirectory = storageDirectory;
    }
}
