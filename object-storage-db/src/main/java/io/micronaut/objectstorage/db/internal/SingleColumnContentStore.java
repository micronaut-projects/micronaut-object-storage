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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Stores each object in one binary column of the object table. Writes bind the content as a stream on insert;
 * reads fetch fixed-size windows with a {@code SUBSTRING} query or {@link Blob#getBytes(long, int)}, depending on
 * the dialect. Multipart parts are staged as chunk rows and concatenated when the upload completes.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class SingleColumnContentStore implements DbContentStore {

    private final ChunkedContentStore parts = new ChunkedContentStore(DbContentLayout.CHUNKED);

    @Override
    @NonNull
    public DbContentLayout getLayout() {
        return DbContentLayout.SINGLE_COLUMN;
    }

    @Override
    public boolean bindsContentOnInsert() {
        return true;
    }

    @Override
    public void bindContent(@NonNull DbStorageContext context, @NonNull PreparedStatement ps, int index,
                            @NonNull InputStream content) throws SQLException {
        ps.setBinaryStream(index, content);
    }

    @Override
    public void writeContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object,
                             @NonNull InputStream content) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + context.mapping().objectTable() + " SET "
            + context.mapping().contentColumn() + " = ?" + DbObjectSql.versionWhere(context.mapping(), object))) {
            ps.setBinaryStream(1, content);
            DbObjectSql.bindVersion(context.mapping(), object, ps, 2);
            ps.executeUpdate();
        }
    }

    @Override
    public byte @Nullable [] readWindow(@NonNull DbStorageContext context, @NonNull Connection connection,
                                        @NonNull DbObjectRef object, long index, int length) throws SQLException {
        DbTableMapping mapping = context.mapping();
        String column = Objects.requireNonNull(mapping.contentColumn(), "content column");
        long offset = index * context.chunkSize();
        String substring = context.sql().substringBinary(column);
        if (substring != null) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT " + substring + " FROM " + mapping.objectTable()
                + DbObjectSql.versionWhere(mapping, object))) {
                ps.setLong(1, offset + 1);
                ps.setInt(2, length);
                DbObjectSql.bindVersion(mapping, object, ps, 3);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    byte[] bytes = rs.getBytes(1);
                    return bytes == null ? new byte[0] : bytes;
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + column + " FROM " + mapping.objectTable()
            + DbObjectSql.versionWhere(mapping, object))) {
            DbObjectSql.bindVersion(mapping, object, ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Blob blob = rs.getBlob(1);
                if (blob == null) {
                    return new byte[0];
                }
                try {
                    long remaining = blob.length() - offset;
                    if (remaining <= 0) {
                        return new byte[0];
                    }
                    return blob.getBytes(offset + 1, (int) Math.min(length, remaining));
                } finally {
                    blob.free();
                }
            }
        }
    }

    @Override
    public void deleteContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object) {
        // the content goes with the row
    }

    @Override
    public void deleteBucketContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull String bucket) throws SQLException {
        parts.deleteBucketParts(connection, bucket);
    }

    @Override
    @NonNull
    public List<String> getCopiedColumns(@NonNull DbTableMapping mapping) {
        return List.of(Objects.requireNonNull(mapping.contentColumn(), "content column"));
    }

    @Override
    public void copyContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef source,
                            @NonNull DbObjectRef target) {
        // copied with the row
    }

    @Override
    public void writePart(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbPartRef part,
                          @NonNull InputStream content) throws SQLException, IOException {
        parts.writePart(context, connection, part, content);
    }

    @Override
    public void deleteParts(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull List<DbPartRef> parts) throws SQLException {
        this.parts.deleteParts(context, connection, parts);
    }

    @Override
    public void assemble(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef target,
                         @NonNull List<DbPartRef> parts) throws SQLException, IOException {
        // The parts are read on the same connection that writes the object, so they are spooled to a temporary file
        // first: a driver can't run a query while it streams a parameter of another statement.
        Path spool = Files.createTempFile("micronaut-object-storage-db", ".part");
        try {
            try (OutputStream out = Files.newOutputStream(spool)) {
                for (DbPartRef part : parts) {
                    ChunkedContentStore.readChunks(connection, part.contentId(), out::write);
                }
            }
            try (InputStream in = Files.newInputStream(spool);
                 PreparedStatement ps = connection.prepareStatement("UPDATE " + context.mapping().objectTable() + " SET "
                     + context.mapping().contentColumn() + " = ?" + DbObjectSql.versionWhere(context.mapping(), target))) {
                ps.setBinaryStream(1, in, Files.size(spool));
                DbObjectSql.bindVersion(context.mapping(), target, ps, 2);
                ps.executeUpdate();
            }
        } finally {
            Files.deleteIfExists(spool);
        }
    }
}
