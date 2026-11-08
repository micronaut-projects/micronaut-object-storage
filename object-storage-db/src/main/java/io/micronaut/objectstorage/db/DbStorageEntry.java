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

import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.db.internal.DbObjectSql;
import io.micronaut.objectstorage.db.internal.JsonMaps;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An {@link ObjectStorageEntry} of the database storage. Each call to {@link #getInputStream()} returns a new stream
 * over the content, which reads it in windows on demand.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
public final class DbStorageEntry implements ObjectStorageEntry<DbStorageObject> {

    private final DbStorageObject object;
    private final Map<String, String> metadata;
    private final Supplier<InputStream> content;

    DbStorageEntry(DbObjectSql.DbObjectRow row, Supplier<InputStream> content) {
        this.object = row.toStorageObject();
        this.metadata = Map.copyOf(JsonMaps.read(row.metadataJson()));
        this.content = content;
    }

    @Override
    @NonNull
    public String getKey() {
        return object.key();
    }

    /**
     * Opens a new stream over the content. No database work happens until the first read. Close the stream when
     * done, so that its read buffer budget is returned.
     *
     * @return The content.
     */
    @Override
    @NonNull
    public InputStream getInputStream() {
        return content.get();
    }

    @Override
    @NonNull
    public DbStorageObject getNativeEntry() {
        return object;
    }

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(object.contentType());
    }
}
