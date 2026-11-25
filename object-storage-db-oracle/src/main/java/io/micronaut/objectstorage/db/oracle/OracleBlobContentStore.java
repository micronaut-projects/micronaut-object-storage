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
package io.micronaut.objectstorage.db.oracle;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.db.DbContentLayout;
import io.micronaut.objectstorage.db.internal.DbObjectSql;
import io.micronaut.objectstorage.db.internal.SingleColumnContentStore;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbObjectRef;
import io.micronaut.objectstorage.db.spi.DbPartRef;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Stores each object in the SecureFile BLOB column of its row.
 *
 * <ul>
 *     <li>Uploads to the built-in tables write through the LOB locator of an empty BLOB, so the driver never buffers
 *     the content.</li>
 *     <li>Downloads read windows with {@link Blob#getBytes(long, int)}.</li>
 *     <li>Multipart parts are BLOBs of their part rows, appended to the object with {@code DBMS_LOB.APPEND} inside
 *     the database on completion.</li>
 *     <li>Copies duplicate the BLOB inside the database with the row.</li>
 * </ul>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
final class OracleBlobContentStore implements DbContentStore {

    private static final String APPEND_PART = """
        DECLARE
            target BLOB;
            source BLOB;
        BEGIN
            SELECT %2$s INTO target FROM %1$s WHERE %3$s = ? FOR UPDATE;
            SELECT CONTENT INTO source FROM %4$s WHERE UPLOAD_ID = ? AND PART_NUMBER = ?;
            IF source IS NOT NULL AND DBMS_LOB.GETLENGTH(source) > 0 THEN
                DBMS_LOB.APPEND(target, source);
            END IF;
        END;""";

    private final SingleColumnContentStore singleColumn = new SingleColumnContentStore();
    private final boolean builtIn;

    /**
     * @param mapping The table mapping.
     */
    OracleBlobContentStore(@NonNull DbTableMapping mapping) {
        this.builtIn = mapping.builtIn();
    }

    @Override
    @NonNull
    public DbContentLayout getLayout() {
        return DbContentLayout.SINGLE_COLUMN;
    }

    @Override
    public boolean bindsContentOnInsert() {
        // a mapped table may declare its content column NOT NULL, so the content is part of the insert there
        return !builtIn;
    }

    @Override
    public void bindContent(@NonNull DbStorageContext context, @NonNull PreparedStatement ps, int index,
                            @NonNull InputStream content) throws SQLException {
        singleColumn.bindContent(context, ps, index, content);
    }

    @Override
    public void writeContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object,
                             @NonNull InputStream content) throws SQLException, IOException {
        DbTableMapping mapping = context.mapping();
        String table = mapping.objectTable();
        String column = contentColumn(mapping);
        String where = DbObjectSql.versionWhere(mapping, object);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + table + " SET " + column + " = EMPTY_BLOB()" + where)) {
            DbObjectSql.bindVersion(mapping, object, ps, 1);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + column + " FROM " + table + where + " FOR UPDATE")) {
            DbObjectSql.bindVersion(mapping, object, ps, 1);
            writeLocked(ps, content, "object " + object.key());
        }
    }

    @Override
    public byte @Nullable [] readWindow(@NonNull DbStorageContext context, @NonNull Connection connection,
                                        @NonNull DbObjectRef object, long index, int length) throws SQLException {
        return singleColumn.readWindow(context, connection, object, index, length);
    }

    @Override
    public void deleteContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object) {
        // the BLOB goes with the row
    }

    @Override
    public void deleteBucketContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull String bucket) {
        // object and part BLOBs go with their rows
    }

    @Override
    @NonNull
    public List<String> getCopiedColumns(@NonNull DbTableMapping mapping) {
        return List.of(contentColumn(mapping));
    }

    @Override
    public void copyContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef source,
                            @NonNull DbObjectRef target) {
        // the BLOB is copied with the row, inside the database
    }

    @Override
    public void writePart(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbPartRef part,
                          @NonNull InputStream content) throws SQLException, IOException {
        String table = context.mapping().uploadPartTable();
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + table
            + " SET CONTENT = EMPTY_BLOB() WHERE UPLOAD_ID = ? AND PART_NUMBER = ?")) {
            ps.setString(1, part.uploadId());
            ps.setInt(2, part.partNumber());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT CONTENT FROM " + table
            + " WHERE UPLOAD_ID = ? AND PART_NUMBER = ? FOR UPDATE")) {
            ps.setString(1, part.uploadId());
            ps.setInt(2, part.partNumber());
            writeLocked(ps, content, "part " + part.partNumber() + " of multipart upload " + part.uploadId());
        }
    }

    @Override
    public void deleteParts(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull List<DbPartRef> parts) {
        // part BLOBs go with their rows
    }

    @Override
    public void assemble(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef target,
                         @NonNull List<DbPartRef> parts) throws SQLException {
        DbTableMapping mapping = context.mapping();
        String column = contentColumn(mapping);
        String idColumn = Objects.requireNonNull(mapping.idColumn(), "multipart uploads need the built-in tables");
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + mapping.objectTable() + " SET " + column
            + " = EMPTY_BLOB() WHERE " + idColumn + " = ?")) {
            ps.setString(1, target.id());
            ps.executeUpdate();
        }
        try (CallableStatement call = connection.prepareCall(APPEND_PART.formatted(mapping.objectTable(), column, idColumn,
            mapping.uploadPartTable()))) {
            for (DbPartRef part : parts) {
                call.setString(1, target.id());
                call.setString(2, part.uploadId());
                call.setInt(3, part.partNumber());
                call.execute();
            }
        }
    }

    private static void writeLocked(PreparedStatement select, InputStream content, String description) throws SQLException, IOException {
        try (ResultSet rs = select.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("The row of " + description + " no longer exists");
            }
            Blob blob = rs.getBlob(1);
            try (OutputStream out = blob.setBinaryStream(1)) {
                content.transferTo(out);
            } finally {
                blob.free();
            }
        }
    }

    private static String contentColumn(DbTableMapping mapping) {
        return Objects.requireNonNull(mapping.contentColumn(), "content column");
    }
}
