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
import io.micronaut.objectstorage.db.DbStorageObject;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbObjectRef;
import io.micronaut.objectstorage.db.spi.DbSqlDialect;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The SQL of object rows: lookups, inserts, deletes, listing, copies and metadata updates, over either the built-in
 * object table or a mapped table.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class DbObjectSql {

    private final DbStorageContext context;
    private final DbTableMapping mapping;
    private final DbSqlDialect sql;
    private final String bucket;

    /**
     * @param context The storage context.
     */
    public DbObjectSql(@NonNull DbStorageContext context) {
        this.context = context;
        this.mapping = context.mapping();
        this.sql = context.sql();
        this.bucket = context.configuration().getBucket();
    }

    /**
     * Builds the {@code WHERE} clause that matches one version of an object: its id in the built-in table, or its key,
     * bucket and entity tag (when mapped and known) in a mapped table.
     *
     * @param mapping The table mapping.
     * @param object The object.
     * @return The clause, with a leading space.
     */
    @NonNull
    public static String versionWhere(@NonNull DbTableMapping mapping, @NonNull DbObjectRef object) {
        if (mapping.idColumn() != null) {
            return " WHERE " + mapping.idColumn() + " = ?";
        }
        StringBuilder where = new StringBuilder(" WHERE ").append(mapping.keyColumn()).append(" = ?");
        if (mapping.bucketColumn() != null) {
            where.append(" AND ").append(mapping.bucketColumn()).append(" = ?");
        }
        if (mapping.etagColumn() != null && object.etag() != null) {
            where.append(" AND ").append(mapping.etagColumn()).append(" = ?");
        }
        return where.toString();
    }

    /**
     * Binds the parameters of {@link #versionWhere(DbTableMapping, DbObjectRef)}.
     *
     * @param mapping The table mapping.
     * @param object The object.
     * @param ps The statement.
     * @param index The index of the first parameter.
     * @return The index of the next parameter.
     * @throws SQLException If binding fails.
     */
    public static int bindVersion(@NonNull DbTableMapping mapping, @NonNull DbObjectRef object,
                                  @NonNull PreparedStatement ps, int index) throws SQLException {
        if (mapping.idColumn() != null) {
            ps.setString(index++, object.id());
            return index;
        }
        ps.setString(index++, object.key());
        if (mapping.bucketColumn() != null) {
            ps.setString(index++, object.bucket());
        }
        if (mapping.etagColumn() != null && object.etag() != null) {
            ps.setString(index++, object.etag());
        }
        return index;
    }

    /**
     * @return The bucket of the storage.
     */
    @NonNull
    public String bucket() {
        return bucket;
    }

    /**
     * Finds the current version of an object.
     *
     * @param connection The connection.
     * @param key The key.
     * @return The row, if the object exists.
     * @throws SQLException If the query fails.
     */
    @NonNull
    public Optional<DbObjectRow> find(@NonNull Connection connection, @NonNull String key) throws SQLException {
        List<String> columns = new ArrayList<>();
        columns.add(mapping.keyColumn());
        addIfMapped(columns, mapping.idColumn());
        addIfMapped(columns, mapping.contentTypeColumn());
        addIfMapped(columns, mapping.contentLengthColumn());
        addIfMapped(columns, mapping.etagColumn());
        if (mapping.metadataColumn() != null) {
            columns.add(sql.selectJson(mapping.metadataColumn(), mapping.builtIn()));
        }
        if (mapping.attributesColumn() != null) {
            columns.add(sql.selectJson(mapping.attributesColumn(), mapping.builtIn()));
        }
        addIfMapped(columns, mapping.updatedAtColumn());
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + String.join(", ", columns) + " FROM "
            + mapping.objectTable() + keyWhere())) {
            bindKey(ps, 1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int i = 2;
                String id = mapping.idColumn() != null ? rs.getString(i++) : null;
                String contentType = mapping.contentTypeColumn() != null ? rs.getString(i++) : null;
                Long contentLength = null;
                if (mapping.contentLengthColumn() != null) {
                    long value = rs.getLong(i++);
                    contentLength = rs.wasNull() ? null : value;
                }
                String etag = mapping.etagColumn() != null ? rs.getString(i++) : null;
                String metadata = mapping.metadataColumn() != null ? rs.getString(i++) : null;
                String attributes = mapping.attributesColumn() != null ? rs.getString(i++) : null;
                Instant updatedAt = mapping.updatedAtColumn() != null ? sql.readTimestamp(rs, i) : null;
                return Optional.of(new DbObjectRow(
                    new DbObjectRef(id, bucket, key, contentLength, etag),
                    contentType,
                    metadata,
                    attributes,
                    updatedAt
                ));
            }
        }
    }

    /**
     * @param connection The connection.
     * @param key The key.
     * @return Whether the object exists.
     * @throws SQLException If the query fails.
     */
    public boolean exists(@NonNull Connection connection, @NonNull String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + mapping.objectTable() + keyWhere())) {
            bindKey(ps, 1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Inserts the row of a new object version.
     *
     * @param connection The connection.
     * @param key The key.
     * @param contentType The content type.
     * @param metadataJson The user metadata as JSON.
     * @param now The time of the upload.
     * @param extraColumns Values of extra columns of a mapped table.
     * @param store The content store.
     * @param content The content, bound on insert when the store does so.
     * @return The new object version.
     * @throws SQLException If the insert fails.
     */
    @NonNull
    public DbObjectRef insert(@NonNull Connection connection,
                              @NonNull String key,
                              @Nullable String contentType,
                              @Nullable String metadataJson,
                              @NonNull Instant now,
                              @NonNull Map<String, @Nullable Object> extraColumns,
                              @NonNull DbContentStore store,
                              @Nullable InputStream content) throws SQLException {
        String id = mapping.builtIn() ? UUID.randomUUID().toString() : null;
        List<String> columns = new ArrayList<>();
        List<Binder> binders = new ArrayList<>();
        if (id != null) {
            columns.add(mapping.idColumn());
            binders.add((ps, i) -> ps.setString(i, id));
        }
        if (mapping.bucketColumn() != null) {
            columns.add(mapping.bucketColumn());
            binders.add((ps, i) -> ps.setString(i, bucket));
        }
        columns.add(mapping.keyColumn());
        binders.add((ps, i) -> ps.setString(i, key));
        addContentInfo(columns, binders, contentType, metadataJson, now);
        if (mapping.createdAtColumn() != null) {
            columns.add(mapping.createdAtColumn());
            binders.add((ps, i) -> sql.bindTimestamp(ps, i, now));
        }
        if (store.bindsContentOnInsert() && content != null) {
            columns.add(mapping.contentColumn());
            binders.add((ps, i) -> store.bindContent(context, ps, i, content));
        }
        addExtraColumns(columns, binders, extraColumns);
        StringBuilder insert = new StringBuilder("INSERT INTO ").append(mapping.objectTable()).append(" (")
            .append(String.join(", ", columns)).append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            insert.append(i == 0 ? "?" : ", ?");
        }
        insert.append(')');
        try (PreparedStatement ps = connection.prepareStatement(insert.toString())) {
            bind(ps, binders, 1);
            ps.executeUpdate();
        }
        return new DbObjectRef(id, bucket, key, null, null);
    }

    /**
     * Overwrites the row of an existing object in a mapped table in place, so that rows referencing it by key keep
     * working.
     *
     * @param connection The connection.
     * @param key The key.
     * @param contentType The content type.
     * @param metadataJson The user metadata as JSON.
     * @param now The time of the upload.
     * @param extraColumns Values of extra columns.
     * @param store The content store.
     * @param content The content.
     * @return The object, with an unknown entity tag until the content is written.
     * @throws SQLException If the update fails.
     */
    @NonNull
    public DbObjectRef overwriteMapped(@NonNull Connection connection,
                                       @NonNull String key,
                                       @Nullable String contentType,
                                       @Nullable String metadataJson,
                                       @NonNull Instant now,
                                       @NonNull Map<String, @Nullable Object> extraColumns,
                                       @NonNull DbContentStore store,
                                       @NonNull InputStream content) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Binder> binders = new ArrayList<>();
        columns.add(mapping.contentColumn());
        binders.add((ps, i) -> store.bindContent(context, ps, i, content));
        addContentInfo(columns, binders, contentType, metadataJson, now);
        addExtraColumns(columns, binders, extraColumns);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + mapping.objectTable() + " SET "
            + String.join(" = ?, ", columns) + " = ?" + keyWhere())) {
            int next = bind(ps, binders, 1);
            bindKey(ps, next, key);
            ps.executeUpdate();
        }
        return new DbObjectRef(null, bucket, key, null, null);
    }

    /**
     * Records the length and entity tag of content that was just written.
     *
     * @param connection The connection.
     * @param object The object version, as returned by the insert.
     * @param length The content length.
     * @param etag The entity tag.
     * @throws SQLException If the update fails.
     */
    public void updateContentInfo(@NonNull Connection connection, @NonNull DbObjectRef object, long length,
                                  @NonNull String etag) throws SQLException {
        List<String> columns = new ArrayList<>();
        addIfMapped(columns, mapping.contentLengthColumn());
        addIfMapped(columns, mapping.etagColumn());
        if (columns.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + mapping.objectTable() + " SET "
            + String.join(" = ?, ", columns) + " = ?" + versionWhere(mapping, object))) {
            int i = 1;
            if (mapping.contentLengthColumn() != null) {
                ps.setLong(i++, length);
            }
            if (mapping.etagColumn() != null) {
                ps.setString(i++, etag);
            }
            bindVersion(mapping, object, ps, i);
            ps.executeUpdate();
        }
    }

    /**
     * Deletes the row of an object version. The caller deletes its content first.
     *
     * @param connection The connection.
     * @param object The object version.
     * @return Whether a row was deleted.
     * @throws SQLException If the delete fails.
     */
    public boolean delete(@NonNull Connection connection, @NonNull DbObjectRef object) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + mapping.objectTable() + versionWhere(mapping, object))) {
            bindVersion(mapping, object, ps, 1);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Lists keys in binary order.
     *
     * @param connection The connection.
     * @param prefix The key prefix, if any.
     * @param after The key after which to start, if any.
     * @param limit The maximum number of keys.
     * @return The keys.
     * @throws SQLException If the query fails.
     */
    @NonNull
    public List<String> listKeys(@NonNull Connection connection, @Nullable String prefix, @Nullable String after,
                                 int limit) throws SQLException {
        List<String> conditions = new ArrayList<>();
        if (mapping.bucketColumn() != null) {
            conditions.add(mapping.bucketColumn() + " = ?");
        }
        if (prefix != null) {
            conditions.add(mapping.keyColumn() + " LIKE ?" + sql.likeEscape());
        }
        if (after != null) {
            conditions.add(sql.binaryKey(mapping.keyColumn()) + " > " + sql.binaryKey("?"));
        }
        String query = "SELECT " + mapping.keyColumn() + " FROM " + mapping.objectTable()
            + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
            + " ORDER BY " + sql.binaryKey(mapping.keyColumn());
        try (PreparedStatement ps = connection.prepareStatement(sql.limit(query, limit))) {
            int i = 1;
            if (mapping.bucketColumn() != null) {
                ps.setString(i++, bucket);
            }
            if (prefix != null) {
                ps.setString(i++, sql.likePrefix(prefix));
            }
            if (after != null) {
                ps.setString(i, after);
            }
            List<String> keys = new ArrayList<>(Math.min(limit, 1024));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
            return keys;
        }
    }

    /**
     * Copies the row of an object to a new key, server side. The caller copies content the row does not carry.
     *
     * @param connection The connection.
     * @param source The source object.
     * @param targetKey The target key, which must not exist.
     * @param now The time of the copy.
     * @param copiedColumns Content columns copied with the row.
     * @return The new object version.
     * @throws SQLException If the insert fails.
     */
    @NonNull
    public DbObjectRef copy(@NonNull Connection connection, @NonNull DbObjectRow source, @NonNull String targetKey,
                            @NonNull Instant now, @NonNull List<String> copiedColumns) throws SQLException {
        String id = mapping.builtIn() ? UUID.randomUUID().toString() : null;
        List<String> targets = new ArrayList<>();
        List<String> values = new ArrayList<>();
        if (id != null) {
            targets.add(mapping.idColumn());
            values.add("?");
        }
        if (mapping.bucketColumn() != null) {
            targets.add(mapping.bucketColumn());
            values.add(mapping.bucketColumn());
        }
        targets.add(mapping.keyColumn());
        values.add("?");
        for (String column : new String[] {mapping.contentTypeColumn(), mapping.contentLengthColumn(), mapping.etagColumn(),
            mapping.metadataColumn(), mapping.attributesColumn()}) {
            if (column != null) {
                targets.add(column);
                values.add(column);
            }
        }
        int timestamps = 0;
        for (String column : new String[] {mapping.createdAtColumn(), mapping.updatedAtColumn()}) {
            if (column != null) {
                targets.add(column);
                values.add("?");
                timestamps++;
            }
        }
        for (String column : copiedColumns) {
            targets.add(column);
            values.add(column);
        }
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + mapping.objectTable() + " ("
            + String.join(", ", targets) + ") SELECT " + String.join(", ", values) + " FROM " + mapping.objectTable()
            + versionWhere(mapping, source.ref()))) {
            int i = 1;
            if (id != null) {
                ps.setString(i++, id);
            }
            ps.setString(i++, targetKey);
            for (int t = 0; t < timestamps; t++) {
                sql.bindTimestamp(ps, i++, now);
            }
            bindVersion(mapping, source.ref(), ps, i);
            ps.executeUpdate();
        }
        DbObjectRef ref = source.ref();
        return new DbObjectRef(id, bucket, targetKey, ref.contentLength(), ref.etag());
    }

    /**
     * Replaces the metadata snapshot of an object.
     *
     * @param connection The connection.
     * @param key The key.
     * @param metadataJson The user metadata as JSON, or {@code null} to delete the snapshot.
     * @param attributesJson The attributes as JSON, or {@code null}.
     * @param contentType The content type.
     * @param replaceContentType Whether to replace the content type.
     * @return Whether the object exists.
     * @throws SQLException If the update fails.
     */
    public boolean updateMetadata(@NonNull Connection connection, @NonNull String key, @Nullable String metadataJson,
                                  @Nullable String attributesJson, @Nullable String contentType,
                                  boolean replaceContentType) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Binder> binders = new ArrayList<>();
        if (mapping.metadataColumn() != null) {
            columns.add(mapping.metadataColumn());
            binders.add((ps, i) -> sql.bindJson(ps, i, metadataJson));
        }
        if (mapping.attributesColumn() != null) {
            columns.add(mapping.attributesColumn());
            binders.add((ps, i) -> sql.bindJson(ps, i, attributesJson));
        }
        if (replaceContentType && mapping.contentTypeColumn() != null) {
            columns.add(mapping.contentTypeColumn());
            binders.add((ps, i) -> setNullableString(ps, i, contentType));
        }
        if (columns.isEmpty()) {
            return exists(connection, key);
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + mapping.objectTable() + " SET "
            + String.join(" = ?, ", columns) + " = ?" + keyWhere())) {
            int next = bind(ps, binders, 1);
            bindKey(ps, next, key);
            return ps.executeUpdate() > 0;
        }
    }

    private void addContentInfo(List<String> columns, List<Binder> binders, @Nullable String contentType,
                                @Nullable String metadataJson, Instant now) {
        if (mapping.contentTypeColumn() != null) {
            columns.add(mapping.contentTypeColumn());
            binders.add((ps, i) -> setNullableString(ps, i, contentType));
        }
        if (mapping.metadataColumn() != null) {
            columns.add(mapping.metadataColumn());
            binders.add((ps, i) -> sql.bindJson(ps, i, metadataJson));
        }
        if (mapping.updatedAtColumn() != null) {
            columns.add(mapping.updatedAtColumn());
            binders.add((ps, i) -> sql.bindTimestamp(ps, i, now));
        }
    }

    private static void addExtraColumns(List<String> columns, List<Binder> binders, Map<String, @Nullable Object> extraColumns) {
        extraColumns.forEach((column, value) -> {
            columns.add(column);
            binders.add((ps, i) -> {
                if (value == null) {
                    ps.setNull(i, Types.NULL);
                } else {
                    ps.setObject(i, value);
                }
            });
        });
    }

    private String keyWhere() {
        return " WHERE " + mapping.keyColumn() + " = ?" + (mapping.bucketColumn() != null ? " AND " + mapping.bucketColumn() + " = ?" : "");
    }

    private int bindKey(PreparedStatement ps, int index, String key) throws SQLException {
        ps.setString(index++, key);
        if (mapping.bucketColumn() != null) {
            ps.setString(index++, bucket);
        }
        return index;
    }

    private static int bind(PreparedStatement ps, List<Binder> binders, int index) throws SQLException {
        for (Binder binder : binders) {
            binder.bind(ps, index++);
        }
        return index;
    }

    private static void setNullableString(PreparedStatement ps, int index, @Nullable String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void addIfMapped(List<String> columns, @Nullable String column) {
        if (column != null) {
            columns.add(column);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps, int index) throws SQLException;
    }

    /**
     * An object row.
     *
     * @param ref The object version.
     * @param contentType The content type.
     * @param metadataJson The user metadata as JSON, {@code null} when there is no metadata snapshot.
     * @param attributesJson The attributes as JSON.
     * @param updatedAt The last modification time.
     */
    public record DbObjectRow(@NonNull DbObjectRef ref,
                              @Nullable String contentType,
                              @Nullable String metadataJson,
                              @Nullable String attributesJson,
                              @Nullable Instant updatedAt) {

        /**
         * @return The native object representation.
         */
        @NonNull
        public DbStorageObject toStorageObject() {
            return new DbStorageObject(ref.bucket(), ref.key(), ref.contentLength(), contentType, ref.etag(), updatedAt);
        }

        /**
         * @return Whether the row carries a metadata snapshot.
         */
        public boolean hasMetadata() {
            return metadataJson != null || attributesJson != null;
        }
    }
}
