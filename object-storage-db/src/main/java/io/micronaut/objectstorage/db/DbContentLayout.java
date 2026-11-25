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
package io.micronaut.objectstorage.db;

/**
 * How the bytes of each object are stored.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
public enum DbContentLayout {

    /**
     * Fixed-size chunk rows in a separate table. Portable across databases, with bounded memory on every driver.
     * The default of the generic module.
     */
    CHUNKED,

    /**
     * One LOB column in the object table. On Oracle, with the Oracle module, a SecureFile BLOB read and written in
     * windows. Elsewhere, whether memory stays bounded depends on the JDBC driver.
     */
    SINGLE_COLUMN,

    /**
     * PostgreSQL Large Objects. Requires the PostgreSQL module; its default on PostgreSQL.
     */
    LARGE_OBJECT,

    /**
     * Chunk rows stored as PostgreSQL {@code bytea} values. The same as {@link #CHUNKED}, named for PostgreSQL
     * users who need logical replication or schema-filtered dumps, which skip Large Objects.
     */
    BYTEA_CHUNKS
}
