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
package io.micronaut.objectstorage.db.spi;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.db.DbStorageConfiguration;
import org.jspecify.annotations.NonNull;

/**
 * What a content store needs to know about the storage it serves.
 *
 * @param configuration The storage configuration.
 * @param mapping The table mapping.
 * @param sql The SQL dialect.
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public record DbStorageContext(@NonNull DbStorageConfiguration configuration,
                               @NonNull DbTableMapping mapping,
                               @NonNull DbSqlDialect sql) {

    /**
     * @return The storage name.
     */
    @NonNull
    public String storageName() {
        return configuration.getName();
    }

    /**
     * @return The chunk and window size in bytes.
     */
    public int chunkSize() {
        return configuration.getChunkSize();
    }
}
