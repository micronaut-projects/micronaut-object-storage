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
import io.micronaut.objectstorage.db.DbContentLayout;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbObjectRef;
import io.micronaut.objectstorage.db.spi.DbPartRef;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Stores each object as a PostgreSQL Large Object referenced by the {@code content_oid} column of its row.
 *
 * <ul>
 *     <li>Uploads create a Large Object and write it with {@code lo_put}, one chunk per statement, so neither the
 *     driver nor the server materialises the whole content.</li>
 *     <li>Downloads read windows with {@code lo_get}, which seeks inside the Large Object.</li>
 *     <li>Copies and multipart completion move bytes between Large Objects inside the server, window by window.</li>
 *     <li>The triggers of the built-in schema unlink a Large Object when its row is deleted or points elsewhere, so
 *     none is orphaned, even by deletes made outside the library.</li>
 * </ul>
 *
 * <p>Only SQL functions are used, so the store works with any PostgreSQL JDBC driver.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
final class PostgresLargeObjectContentStore implements DbContentStore {

    @Override
    @NonNull
    public DbContentLayout getLayout() {
        return DbContentLayout.LARGE_OBJECT;
    }

    @Override
    public void writeContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object,
                             @NonNull InputStream content) throws SQLException, IOException {
        long oid = createLargeObject(connection);
        write(connection, oid, content, context.chunkSize());
        setOid(connection, "UPDATE " + context.mapping().objectTable() + " SET content_oid = ? WHERE id = ?", oid, object.id());
    }

    @Override
    public byte @Nullable [] readWindow(@NonNull DbStorageContext context, @NonNull Connection connection,
                                        @NonNull DbObjectRef object, long index, int length) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT content_oid, CASE WHEN content_oid IS NULL THEN NULL"
            + " ELSE lo_get(content_oid, ?, ?) END FROM " + context.mapping().objectTable() + " WHERE id = ?")) {
            ps.setLong(1, index * context.chunkSize());
            ps.setInt(2, length);
            ps.setString(3, object.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                byte[] bytes = rs.getBytes(2);
                return bytes == null ? new byte[0] : bytes;
            }
        }
    }

    @Override
    public void deleteContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object) {
        // the trigger of the object table unlinks the Large Object with the row
    }

    @Override
    public void deleteBucketContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull String bucket) {
        // the triggers of the object and part tables unlink the Large Objects with the rows
    }

    @Override
    @NonNull
    public List<String> getCopiedColumns(@NonNull DbTableMapping mapping) {
        // never share a Large Object between two rows: deleting one would unlink the other's content
        return List.of();
    }

    @Override
    public void copyContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef source,
                            @NonNull DbObjectRef target) throws SQLException {
        String table = context.mapping().objectTable();
        Long sourceOid = findOid(connection, "SELECT content_oid FROM " + table + " WHERE id = ?", source.id());
        if (sourceOid == null) {
            return;
        }
        long oid = createLargeObject(connection);
        copy(connection, sourceOid, oid, 0, context.chunkSize());
        setOid(connection, "UPDATE " + table + " SET content_oid = ? WHERE id = ?", oid, target.id());
    }

    @Override
    public void writePart(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbPartRef part,
                          @NonNull InputStream content) throws SQLException, IOException {
        long oid = createLargeObject(connection);
        write(connection, oid, content, context.chunkSize());
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + context.mapping().uploadPartTable()
            + " SET content_oid = ? WHERE upload_id = ? AND part_number = ?")) {
            ps.setLong(1, oid);
            ps.setString(2, part.uploadId());
            ps.setInt(3, part.partNumber());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteParts(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull List<DbPartRef> parts) {
        // the trigger of the part table unlinks the Large Objects with the rows
    }

    @Override
    public void assemble(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef target,
                         @NonNull List<DbPartRef> parts) throws SQLException {
        long oid = createLargeObject(connection);
        long offset = 0;
        try (PreparedStatement find = connection.prepareStatement("SELECT content_oid FROM " + context.mapping().uploadPartTable()
            + " WHERE upload_id = ? AND part_number = ?")) {
            for (DbPartRef part : parts) {
                find.setString(1, part.uploadId());
                find.setInt(2, part.partNumber());
                Long partOid = null;
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        long value = rs.getLong(1);
                        partOid = rs.wasNull() ? null : value;
                    }
                }
                if (partOid != null) {
                    offset = copy(connection, partOid, oid, offset, context.chunkSize());
                }
            }
        }
        setOid(connection, "UPDATE " + context.mapping().objectTable() + " SET content_oid = ? WHERE id = ?", oid, target.id());
    }

    private static long createLargeObject(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT lo_create(0)");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void write(Connection connection, long oid, InputStream content, int chunkSize) throws SQLException, IOException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT lo_put(?, ?, ?)")) {
            byte[] buffer = new byte[chunkSize];
            long offset = 0;
            int read;
            while ((read = content.readNBytes(buffer, 0, buffer.length)) > 0) {
                ps.setLong(1, oid);
                ps.setLong(2, offset);
                ps.setBytes(3, read == buffer.length ? buffer : Arrays.copyOf(buffer, read));
                ps.executeQuery().close();
                offset += read;
            }
        }
    }

    /**
     * Appends a Large Object to another inside the server, one window per statement.
     *
     * @return The offset after the copied bytes.
     */
    private static long copy(Connection connection, long source, long target, long targetOffset, int window) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT octet_length(w.data), lo_put(?, ?, w.data) FROM (SELECT lo_get(?, ?, ?) AS data) w")) {
            long sourceOffset = 0;
            while (true) {
                ps.setLong(1, target);
                ps.setLong(2, targetOffset);
                ps.setLong(3, source);
                ps.setLong(4, sourceOffset);
                ps.setInt(5, window);
                int copied;
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    copied = rs.getInt(1);
                }
                sourceOffset += copied;
                targetOffset += copied;
                if (copied < window) {
                    return targetOffset;
                }
            }
        }
    }

    private static @Nullable Long findOid(Connection connection, String sql, String id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long oid = rs.getLong(1);
                return rs.wasNull() ? null : oid;
            }
        }
    }

    private static void setOid(Connection connection, String sql, long oid, @Nullable String id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, oid);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }
}
