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
package io.micronaut.objectstorage.db.internal;

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
 * Provides the portable layouts: {@code CHUNKED} (also as {@code BYTEA_CHUNKS} on PostgreSQL) and
 * {@code SINGLE_COLUMN}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
@Singleton
final class GenericContentStoreProvider implements DbContentStoreProvider {

    @Override
    @NonNull
    public Optional<DbContentLayout> getDefaultLayout(@NonNull Dialect dialect) {
        return Optional.of(DbContentLayout.CHUNKED);
    }

    @Override
    @NonNull
    public Optional<DbContentStore> create(@NonNull Dialect dialect, @NonNull DbContentLayout layout, @NonNull DbTableMapping mapping) {
        return switch (layout) {
            case CHUNKED -> mapping.builtIn() ? Optional.of(new ChunkedContentStore(layout)) : Optional.empty();
            case BYTEA_CHUNKS -> mapping.builtIn() && dialect == Dialect.POSTGRES ? Optional.of(new ChunkedContentStore(layout)) : Optional.empty();
            case SINGLE_COLUMN -> Optional.of(new SingleColumnContentStore());
            default -> Optional.empty();
        };
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
