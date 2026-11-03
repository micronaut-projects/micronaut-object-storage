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
import io.micronaut.objectstorage.db.DbStorageConfiguration;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks a table mapping against the database metadata. Never runs at startup: only on demand, or on first use of
 * a storage with {@code validate-mapping} enabled.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class DbMappingValidation {

    private DbMappingValidation() {
    }

    /**
     * @param connection The connection.
     * @param mapping The mapping.
     * @param store The content store.
     * @return The problems found, empty if the mapping is valid.
     * @throws SQLException If reading metadata fails.
     */
    @NonNull
    public static List<String> validate(@NonNull Connection connection, @NonNull DbTableMapping mapping,
                                        @NonNull DbContentStore store) throws SQLException {
        List<String> problems = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        Map<String, @Nullable String> required = new LinkedHashMap<>();
        if (mapping.builtIn()) {
            List<String> tables = new ArrayList<>(List.of(DbTableMapping.OBJECT_TABLE, DbTableMapping.BUCKET_TABLE,
                DbTableMapping.UPLOAD_TABLE, DbTableMapping.UPLOAD_PART_TABLE, DbTableMapping.CHUNK_TABLE));
            for (String table : tables) {
                if (columns(metaData, table).isEmpty()) {
                    problems.add("table " + table + " does not exist");
                }
            }
            return problems;
        }
        Set<String> columns = columns(metaData, mapping.objectTable());
        if (columns.isEmpty()) {
            problems.add("table " + mapping.objectTable() + " does not exist or has no visible columns");
            return problems;
        }
        required.put("key", mapping.keyColumn());
        required.put("content", mapping.contentColumn());
        required.put("content-length", mapping.contentLengthColumn());
        required.put("content-type", mapping.contentTypeColumn());
        required.put("etag", mapping.etagColumn());
        required.put("metadata", mapping.metadataColumn());
        required.put("bucket", mapping.bucketColumn());
        required.put("updated-at", mapping.updatedAtColumn());
        required.forEach((name, column) -> {
            if (column != null && !columns.contains(normalize(column))) {
                problems.add("column " + column + " (mapped as " + name + ") does not exist in table " + mapping.objectTable());
            }
        });
        return problems;
    }

    /**
     * @param configuration The storage configuration.
     * @param problems The problems.
     * @return A message describing them.
     */
    @NonNull
    public static String message(@NonNull DbStorageConfiguration configuration, @NonNull List<String> problems) {
        return "Invalid table mapping for database storage '" + configuration.getName() + "': " + String.join("; ", problems);
    }

    private static Set<String> columns(DatabaseMetaData metaData, String qualifiedTable) throws SQLException {
        String schema = null;
        String table = qualifiedTable;
        int dot = qualifiedTable.lastIndexOf('.');
        if (dot > 0) {
            schema = qualifiedTable.substring(0, dot);
            table = qualifiedTable.substring(dot + 1);
        }
        for (String[] candidate : List.of(
            new String[] {schema, table},
            new String[] {upper(schema), upper(table)},
            new String[] {lower(schema), lower(table)})) {
            Set<String> found = new TreeSet<>();
            try (ResultSet rs = metaData.getColumns(null, candidate[0], candidate[1], null)) {
                while (rs.next()) {
                    found.add(normalize(rs.getString("COLUMN_NAME")));
                }
            }
            if (!found.isEmpty()) {
                return found;
            }
        }
        return Set.of();
    }

    private static String normalize(String column) {
        String name = column;
        if (name.length() > 1 && (name.startsWith("\"") && name.endsWith("\"") || name.startsWith("`") && name.endsWith("`")
            || name.startsWith("[") && name.endsWith("]"))) {
            name = name.substring(1, name.length() - 1);
        }
        return name.toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static String upper(@Nullable String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static String lower(@Nullable String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
