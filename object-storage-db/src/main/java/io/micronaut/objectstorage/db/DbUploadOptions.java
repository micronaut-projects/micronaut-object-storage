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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Upload options of the database storage, passed to the consumer of
 * {@link DbStorageOperations#upload(io.micronaut.objectstorage.request.UploadRequest, java.util.function.Consumer)}.
 *
 * <p>When a storage is mapped onto an existing table, use {@link #setColumn(String, Object)} to supply values for
 * extra columns of that table, such as audit fields or foreign keys to business rows.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
public final class DbUploadOptions {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*|\"[^\"]+\"");

    private final Map<String, @Nullable Object> columns = new LinkedHashMap<>();

    /**
     * Sets the value of an extra column of a mapped table. The value is bound with
     * {@link java.sql.PreparedStatement#setObject(int, Object)}.
     *
     * @param name The column name: a plain SQL identifier, or a double-quoted one.
     * @param value The value.
     * @return These options.
     */
    @NonNull
    public DbUploadOptions setColumn(@NonNull String name, @Nullable Object value) {
        Objects.requireNonNull(name, "name");
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid column name: " + name);
        }
        columns.put(name, value);
        return this;
    }

    /**
     * @return The extra column values, in the order they were set.
     */
    @NonNull
    public Map<String, @Nullable Object> getColumns() {
        return Collections.unmodifiableMap(columns);
    }
}
