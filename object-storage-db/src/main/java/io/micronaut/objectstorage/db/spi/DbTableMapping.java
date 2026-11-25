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
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.db.DbStorageConfiguration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Resolved table and column names of a database storage, either the built-in tables or a table mapped by the user.
 *
 * @param builtIn Whether the built-in tables are used.
 * @param objectTable The object table.
 * @param idColumn The id column, only for the built-in tables.
 * @param bucketColumn The bucket column, if any.
 * @param keyColumn The key column.
 * @param contentColumn The content column of single column layouts.
 * @param contentLengthColumn The content length column, if any.
 * @param contentTypeColumn The content type column, if any.
 * @param etagColumn The entity tag column, if any.
 * @param metadataColumn The metadata column, if any.
 * @param attributesColumn The attributes column, only for the built-in tables.
 * @param createdAtColumn The creation time column, only for the built-in tables.
 * @param updatedAtColumn The last modification time column, if any.
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public record DbTableMapping(
    boolean builtIn,
    @NonNull String objectTable,
    @Nullable String idColumn,
    @Nullable String bucketColumn,
    @NonNull String keyColumn,
    @Nullable String contentColumn,
    @Nullable String contentLengthColumn,
    @Nullable String contentTypeColumn,
    @Nullable String etagColumn,
    @Nullable String metadataColumn,
    @Nullable String attributesColumn,
    @Nullable String createdAtColumn,
    @Nullable String updatedAtColumn
) {

    public static final String OBJECT_TABLE = "MN_OS_OBJECT";
    public static final String CHUNK_TABLE = "MN_OS_CHUNK";
    public static final String BUCKET_TABLE = "MN_OS_BUCKET";
    public static final String UPLOAD_TABLE = "MN_OS_UPLOAD";
    public static final String UPLOAD_PART_TABLE = "MN_OS_UPLOAD_PART";
    public static final String SCHEMA_HISTORY_TABLE = "MN_OS_SCHEMA_HISTORY";

    /**
     * The mapping of the built-in tables.
     */
    public static final DbTableMapping BUILT_IN = new DbTableMapping(
        true,
        OBJECT_TABLE,
        "ID",
        "BUCKET",
        "OBJECT_KEY",
        "CONTENT",
        "CONTENT_LENGTH",
        "CONTENT_TYPE",
        "ETAG",
        "METADATA",
        "ATTRIBUTES",
        "CREATED_AT",
        "UPDATED_AT"
    );

    /**
     * Resolves the mapping of a storage from its configuration.
     *
     * @param configuration The storage configuration.
     * @return The mapping.
     */
    @NonNull
    public static DbTableMapping of(@NonNull DbStorageConfiguration configuration) {
        DbStorageConfiguration.DbTableConfiguration table = configuration.getTable();
        if (table.getName() == null) {
            return BUILT_IN;
        }
        DbStorageConfiguration.DbTableConfiguration.DbColumnsConfiguration columns = table.getColumns();
        if (columns.getKey() == null || columns.getContent() == null) {
            throw new ObjectStorageException("Database storage '" + configuration.getName() + "' maps table "
                + table.getName() + " but does not map the required columns: set both "
                + DbStorageConfiguration.PREFIX + "." + configuration.getName() + ".table.columns.key and "
                + DbStorageConfiguration.PREFIX + "." + configuration.getName() + ".table.columns.content");
        }
        return new DbTableMapping(
            false,
            table.getName(),
            null,
            columns.getBucket(),
            columns.getKey(),
            columns.getContent(),
            columns.getContentLength(),
            columns.getContentType(),
            columns.getEtag(),
            columns.getMetadata(),
            null,
            null,
            columns.getUpdatedAt()
        );
    }

    /**
     * @return The chunk table of the built-in schema.
     */
    @NonNull
    public String chunkTable() {
        return CHUNK_TABLE;
    }

    /**
     * @return The bucket table of the built-in schema.
     */
    @NonNull
    public String bucketTable() {
        return BUCKET_TABLE;
    }

    /**
     * @return The multipart upload table of the built-in schema.
     */
    @NonNull
    public String uploadTable() {
        return UPLOAD_TABLE;
    }

    /**
     * @return The multipart part table of the built-in schema.
     */
    @NonNull
    public String uploadPartTable() {
        return UPLOAD_PART_TABLE;
    }
}
