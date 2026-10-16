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
package io.micronaut.objectstorage.db.postgres;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.objectstorage.db.DbContentLayout;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbContentStoreProvider;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Provides the Large Object store on PostgreSQL, and makes it the default layout there for the built-in tables.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
@Singleton
final class PostgresContentStoreProvider implements DbContentStoreProvider {

    /**
     * Runs before the generic provider of {@code micronaut-object-storage-db}.
     */
    static final int ORDER = 0;

    @Override
    @NonNull
    public Optional<DbContentLayout> getDefaultLayout(@NonNull Dialect dialect) {
        return dialect == Dialect.POSTGRES ? Optional.of(DbContentLayout.LARGE_OBJECT) : Optional.empty();
    }

    @Override
    @NonNull
    public Optional<DbContentStore> create(@NonNull Dialect dialect, @NonNull DbContentLayout layout, @NonNull DbTableMapping mapping) {
        if (dialect == Dialect.POSTGRES && layout == DbContentLayout.LARGE_OBJECT && mapping.builtIn()) {
            return Optional.of(new PostgresLargeObjectContentStore());
        }
        return Optional.empty();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
