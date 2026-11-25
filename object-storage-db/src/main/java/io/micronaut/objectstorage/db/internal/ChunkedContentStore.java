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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Stores content as fixed-size rows of the {@code MN_OS_CHUNK} table, keyed by content id and sequence number.
 * Objects use their row id as content id; multipart parts use their own. Completing a multipart upload re-links the
 * parts' chunks to the object instead of copying them.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class ChunkedContentStore implements DbContentStore {

    private static final String CHUNK = DbTableMapping.CHUNK_TABLE;
    private static final String OBJECT = DbTableMapping.OBJECT_TABLE;
    private static final String UPLOAD = DbTableMapping.UPLOAD_TABLE;
    private static final String UPLOAD_PART = DbTableMapping.UPLOAD_PART_TABLE;

    private final DbContentLayout layout;

    public ChunkedContentStore(@NonNull DbContentLayout layout) {
        this.layout = layout;
    }

    @Override
    @NonNull
    public DbContentLayout getLayout() {
        return layout;
    }

    @Override
    public void writeContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object,
                             @NonNull InputStream content) throws SQLException, IOException {
        writeChunks(context, connection, Objects.requireNonNull(object.id(), "id"), content);
    }

    @Override
    public byte @Nullable [] readWindow(@NonNull DbStorageContext context, @NonNull Connection connection,
                                        @NonNull DbObjectRef object, long index, int length) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT DATA FROM " + CHUNK + " WHERE CONTENT_ID = ? AND SEQ = ?")) {
            ps.setString(1, object.id());
            ps.setLong(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    @Override
    public void deleteContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object) throws SQLException {
        deleteChunks(connection, Objects.requireNonNull(object.id(), "id"));
    }

    @Override
    public void deleteBucketContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull String bucket) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + CHUNK + " WHERE CONTENT_ID IN (SELECT ID FROM "
            + OBJECT + " WHERE BUCKET = ?)")) {
            ps.setString(1, bucket);
            ps.executeUpdate();
        }
        deleteBucketParts(connection, bucket);
    }

    /**
     * Deletes the chunks of every multipart part of a bucket.
     *
     * @param connection The connection.
     * @param bucket The bucket.
     * @throws SQLException If the statement fails.
     */
    void deleteBucketParts(@NonNull Connection connection, @NonNull String bucket) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + CHUNK + " WHERE CONTENT_ID IN (SELECT P.CONTENT_ID FROM "
            + UPLOAD_PART + " P INNER JOIN " + UPLOAD + " U ON P.UPLOAD_ID = U.UPLOAD_ID WHERE U.BUCKET = ?)")) {
            ps.setString(1, bucket);
            ps.executeUpdate();
        }
    }

    @Override
    public void copyContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef source,
                            @NonNull DbObjectRef target) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + CHUNK + " (CONTENT_ID, SEQ, DATA) SELECT ?, SEQ, DATA FROM "
            + CHUNK + " WHERE CONTENT_ID = ?")) {
            ps.setString(1, target.id());
            ps.setString(2, source.id());
            ps.executeUpdate();
        }
    }

    @Override
    public void writePart(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbPartRef part,
                          @NonNull InputStream content) throws SQLException, IOException {
        writeChunks(context, connection, part.contentId(), content);
    }

    @Override
    public void deleteParts(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull List<DbPartRef> parts) throws SQLException {
        for (DbPartRef part : parts) {
            deleteChunks(connection, part.contentId());
        }
    }

    @Override
    public void assemble(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef target,
                         @NonNull List<DbPartRef> parts) throws SQLException {
        long offset = 0;
        try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM " + CHUNK + " WHERE CONTENT_ID = ?");
             PreparedStatement relink = connection.prepareStatement("UPDATE " + CHUNK + " SET CONTENT_ID = ?, SEQ = SEQ + ? WHERE CONTENT_ID = ?")) {
            for (DbPartRef part : parts) {
                count.setString(1, part.contentId());
                long chunks;
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    chunks = rs.getLong(1);
                }
                if (chunks == 0) {
                    continue;
                }
                relink.setString(1, target.id());
                relink.setLong(2, offset);
                relink.setString(3, part.contentId());
                relink.executeUpdate();
                offset += chunks;
            }
        }
    }

    /**
     * Reads a stream into chunk rows.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param contentId The content id.
     * @param content The content.
     * @throws SQLException If a statement fails.
     * @throws IOException If reading fails.
     */
    static void writeChunks(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull String contentId,
                            @NonNull InputStream content) throws SQLException, IOException {
        int chunkSize = context.chunkSize();
        byte[] buffer = new byte[chunkSize];
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + CHUNK + " (CONTENT_ID, SEQ, DATA) VALUES (?, ?, ?)")) {
            long seq = 0;
            int read;
            while ((read = content.readNBytes(buffer, 0, chunkSize)) > 0) {
                ps.setString(1, contentId);
                ps.setLong(2, seq++);
                ps.setBytes(3, read == chunkSize ? buffer : Arrays.copyOf(buffer, read));
                ps.executeUpdate();
                if (read < chunkSize) {
                    break;
                }
            }
        }
    }

    /**
     * Reads every chunk of a content id, in order.
     *
     * @param connection The connection.
     * @param contentId The content id.
     * @param consumer Receives each chunk.
     * @throws SQLException If a statement fails.
     * @throws IOException If the consumer fails.
     */
    static void readChunks(@NonNull Connection connection, @NonNull String contentId, @NonNull ChunkConsumer consumer) throws SQLException, IOException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT DATA FROM " + CHUNK + " WHERE CONTENT_ID = ? AND SEQ = ?")) {
            long seq = 0;
            while (true) {
                ps.setString(1, contentId);
                ps.setLong(2, seq++);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                    consumer.accept(rs.getBytes(1));
                }
            }
        }
    }

    private static void deleteChunks(Connection connection, String contentId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + CHUNK + " WHERE CONTENT_ID = ?")) {
            ps.setString(1, contentId);
            ps.executeUpdate();
        }
    }

    /**
     * Receives chunks.
     */
    @FunctionalInterface
    interface ChunkConsumer {
        void accept(byte[] chunk) throws IOException;
    }
}
