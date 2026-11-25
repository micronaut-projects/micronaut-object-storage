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
import io.micronaut.objectstorage.db.DbContentLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Stores and reads the bytes of objects and multipart parts for one content layout. Every method runs on a
 * connection of a transaction managed by the caller: the store never commits.
 *
 * <p>Object rows are written by the storage; the store only handles content. Reads go through
 * {@link #readWindow(DbStorageContext, Connection, DbObjectRef, long, int)}, one short transaction per window, so a
 * slow client never pins a pooled connection.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public interface DbContentStore {

    /**
     * @return The layout implemented by this store.
     */
    @NonNull
    DbContentLayout getLayout();

    /**
     * Whether the content is bound as a parameter of the object row {@code INSERT}, which mapped tables with a
     * {@code NOT NULL} content column need. When {@code true}, {@link #bindContent} is called instead of
     * {@link #writeContent}.
     *
     * @return {@code true} if the content is bound on insert.
     */
    default boolean bindsContentOnInsert() {
        return false;
    }

    /**
     * Binds the content to the object row {@code INSERT}.
     *
     * @param context The storage context.
     * @param ps The insert statement.
     * @param index The parameter index of the content column.
     * @param content The content.
     * @throws SQLException If binding fails.
     */
    default void bindContent(@NonNull DbStorageContext context, @NonNull PreparedStatement ps, int index,
                             @NonNull InputStream content) throws SQLException {
        throw new UnsupportedOperationException("Content is not bound on insert by " + getClass().getSimpleName());
    }

    /**
     * Writes the content of an object whose row was just inserted.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param object The object.
     * @param content The content. The store reads it to the end.
     * @throws SQLException If a statement fails.
     * @throws IOException If reading the content fails.
     */
    void writeContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object,
                      @NonNull InputStream content) throws SQLException, IOException;

    /**
     * Reads one window of an object.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param object The object version being read.
     * @param index The 0-based window index. Window {@code n} starts at {@code n * chunkSize} for fixed-size windows;
     *              chunked layouts read chunk {@code n}.
     * @param length The window size.
     * @return The bytes of the window (possibly fewer than {@code length} at the end, or empty past the end), or
     * {@code null} if this version of the object no longer exists.
     * @throws SQLException If a statement fails.
     * @throws IOException If reading fails.
     */
    byte @Nullable [] readWindow(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object,
                                 long index, int length) throws SQLException, IOException;

    /**
     * Deletes the content of an object before its row is deleted.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param object The object.
     * @throws SQLException If a statement fails.
     */
    void deleteContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef object) throws SQLException;

    /**
     * Deletes the content of every object and multipart part of a bucket, before their rows are deleted.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param bucket The bucket.
     * @throws SQLException If a statement fails.
     */
    void deleteBucketContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull String bucket) throws SQLException;

    /**
     * @param mapping The table mapping.
     * @return The columns of the object row copied as-is by {@code INSERT ... SELECT} on copy, such as a LOB column
     * the database duplicates server side.
     */
    @NonNull
    default List<String> getCopiedColumns(@NonNull DbTableMapping mapping) {
        return List.of();
    }

    /**
     * Copies the content of an object to the row of another object, server side, after the target row was inserted.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param source The source object.
     * @param target The target object.
     * @throws SQLException If a statement fails.
     */
    void copyContent(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef source,
                     @NonNull DbObjectRef target) throws SQLException;

    /**
     * Writes the content of a multipart part whose row was just inserted.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param part The part.
     * @param content The content. The store reads it to the end.
     * @throws SQLException If a statement fails.
     * @throws IOException If reading the content fails.
     */
    void writePart(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbPartRef part,
                   @NonNull InputStream content) throws SQLException, IOException;

    /**
     * Deletes the content of multipart parts before their rows are deleted.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param parts The parts.
     * @throws SQLException If a statement fails.
     */
    void deleteParts(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull List<DbPartRef> parts) throws SQLException;

    /**
     * Builds the content of an object, whose row was just inserted, from multipart parts in order. The parts' content
     * may be moved rather than copied; the caller deletes the parts with {@link #deleteParts} afterwards.
     *
     * @param context The storage context.
     * @param connection The connection.
     * @param target The target object.
     * @param parts The parts, in order.
     * @throws SQLException If a statement fails.
     * @throws IOException If moving bytes fails.
     */
    void assemble(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull DbObjectRef target,
                  @NonNull List<DbPartRef> parts) throws SQLException, IOException;
}
