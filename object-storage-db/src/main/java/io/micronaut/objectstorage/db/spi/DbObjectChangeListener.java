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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Observes object changes inside the transaction that makes them, for example to publish database notifications that
 * are delivered only if the transaction commits.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
@FunctionalInterface
public interface DbObjectChangeListener {

    /**
     * Called after an object was created, overwritten or deleted, on the connection of the change.
     *
     * @param context The storage context.
     * @param connection The connection of the transaction that makes the change.
     * @param change The change.
     * @throws SQLException If a statement fails, which rolls the change back.
     */
    void onChange(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull Change change) throws SQLException;

    /**
     * An object change.
     *
     * @param type The type of change.
     * @param bucket The bucket.
     * @param key The object key.
     * @param etag The entity tag of the new content, {@code null} for deletions.
     */
    record Change(@NonNull Type type, @NonNull String bucket, @NonNull String key, @Nullable String etag) {
    }

    /**
     * Types of change.
     */
    enum Type {
        /**
         * The object was created or overwritten, by an upload, a copy or a completed multipart upload.
         */
        UPLOADED,

        /**
         * The object was deleted.
         */
        DELETED
    }
}
