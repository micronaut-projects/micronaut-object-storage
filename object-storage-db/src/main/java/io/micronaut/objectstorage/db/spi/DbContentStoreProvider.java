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
import io.micronaut.core.order.Ordered;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.objectstorage.db.DbContentLayout;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Creates content stores. The generic module provides the portable layouts; the PostgreSQL and Oracle modules
 * register providers with a higher precedence for their dialect. The provider with the highest precedence that
 * supports the requested layout wins.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public interface DbContentStoreProvider extends Ordered {

    /**
     * @param dialect The dialect.
     * @return The layout this provider recommends for the dialect when none is configured, if any.
     */
    @NonNull
    Optional<DbContentLayout> getDefaultLayout(@NonNull Dialect dialect);

    /**
     * Creates the store for a layout.
     *
     * @param dialect The dialect.
     * @param layout The layout.
     * @param mapping The table mapping.
     * @return The store, or empty if this provider does not support the combination.
     */
    @NonNull
    Optional<DbContentStore> create(@NonNull Dialect dialect, @NonNull DbContentLayout layout, @NonNull DbTableMapping mapping);
}
