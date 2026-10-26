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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.db.internal.DbObjectSql;
import io.micronaut.objectstorage.db.internal.DbStorageRuntime;
import io.micronaut.objectstorage.db.internal.JsonMaps;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry;
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Object metadata operations of the database storage. The snapshot lives in the object row, so it commits together
 * with the content: there is no window where content and metadata disagree.
 *
 * <p>The content length, entity tag and last modification time are derived from the stored content and are not
 * changed by {@link #save(ObjectMetadataWrite)}; user metadata, attributes and the content type are replaced.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachBean(DbStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = DbStorageConfiguration.class)
public class DbObjectMetadataOperations implements ObjectMetadataOperations<DbStorageObject> {

    private final DbStorageRuntime runtime;

    DbObjectMetadataOperations(@Parameter DbStorageRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @NonNull
    public Optional<ObjectMetadataEntry<DbStorageObject>> retrieve(@NonNull String key) {
        Optional<DbObjectSql.DbObjectRow> row = runtime.transactions().read(connection -> runtime.objects().find(connection, key));
        DbTableMapping mapping = runtime.context().mapping();
        return row.filter(r -> r.hasMetadata() || mapping.metadataColumn() == null)
            .map(r -> new ObjectMetadataEntry<>(
                key,
                JsonMaps.read(r.metadataJson()),
                JsonMaps.read(r.attributesJson()),
                r.contentType(),
                r.ref().contentLength(),
                r.ref().etag(),
                r.updatedAt(),
                r.toStorageObject()
            ));
    }

    @Override
    public void save(@NonNull ObjectMetadataWrite write) {
        DbTableMapping mapping = runtime.context().mapping();
        if (mapping.metadataColumn() == null && !write.metadata().isEmpty()) {
            throw new ObjectStorageException("Database storage '" + runtime.configuration().getName()
                + "' does not persist user metadata: map a metadata column to enable it");
        }
        if (mapping.attributesColumn() == null && !write.attributes().isEmpty()) {
            throw new ObjectStorageException("Database storage '" + runtime.configuration().getName()
                + "' does not persist metadata attributes on a mapped table");
        }
        boolean updated = runtime.transactions().write(connection -> runtime.objects().updateMetadata(
            connection,
            write.key(),
            JsonMaps.write(write.metadata()),
            JsonMaps.write(write.attributes()),
            write.contentType(),
            true
        ));
        if (!updated) {
            throw new ObjectStorageException("Cannot persist metadata for a missing object: " + write.key());
        }
    }

    @Override
    public void delete(@NonNull String key) {
        runtime.transactions().write(connection -> runtime.objects().updateMetadata(connection, key, null, null, null, false));
    }
}
