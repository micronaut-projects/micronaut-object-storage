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
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.db.internal.DbObjectSql;
import io.micronaut.objectstorage.db.internal.DbStorageRuntime;
import io.micronaut.objectstorage.db.internal.DigestingInputStream;
import io.micronaut.objectstorage.db.internal.JsonMaps;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbObjectRef;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An implementation of {@link ObjectStorageOperations} that stores objects in a relational database through
 * Micronaut Data JDBC.
 *
 * <p>Writes join the caller's transaction on the same datasource according to the configured
 * {@link DbStorageConfiguration#getTransactionPropagation() propagation}, so content, metadata and entity tag commit
 * or roll back together with the caller's own data. Downloads read the content in windows, each with its own short
 * query, so no connection is held while a client consumes the stream.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachBean(DbStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = DbStorageConfiguration.class)
@Primary
public class DbStorageOperations implements ObjectStorageOperations<DbUploadOptions, DbStorageObject, Boolean> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;

    private final DbStorageRuntime runtime;

    /**
     * @param runtime The storage runtime.
     */
    DbStorageOperations(@Parameter DbStorageRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @NonNull
    public UploadResponse<DbStorageObject> upload(@NonNull UploadRequest request) {
        return upload(request, options -> { });
    }

    @Override
    @NonNull
    public UploadResponse<DbStorageObject> upload(@NonNull UploadRequest request, @NonNull Consumer<DbUploadOptions> requestConsumer) {
        DbUploadOptions options = new DbUploadOptions();
        requestConsumer.accept(options);
        if (!options.getColumns().isEmpty()) {
            runtime.requireBuiltInTables("Extra columns");
        }
        String key = request.getKey();
        String contentType = request.getContentType().orElse(null);
        String metadata = JsonMaps.write(request.getMetadata());
        DbStorageContext context = runtime.context();
        DbContentStore store = runtime.store();
        DbObjectSql objects = runtime.objects();
        try (InputStream in = request.getInputStream()) {
            DbStorageObject stored = runtime.transactions().write(connection -> {
                Instant now = Instant.now();
                DigestingInputStream content = new DigestingInputStream(in, context.configuration().getMaxObjectSize());
                Optional<DbObjectSql.DbObjectRow> existing = objects.find(connection, key);
                DbObjectRef object;
                if (existing.isPresent() && !context.mapping().builtIn()) {
                    object = objects.overwriteMapped(connection, key, contentType, metadata, now, options.getColumns(), store, content);
                } else {
                    if (existing.isPresent()) {
                        deleteVersion(connection, existing.get().ref());
                    }
                    object = objects.insert(connection, key, contentType, metadata, now, options.getColumns(), store, content);
                    if (!store.bindsContentOnInsert()) {
                        store.writeContent(context, connection, object, content);
                    }
                }
                content.drain();
                String etag = DigestingInputStream.toHex(content.getDigest());
                objects.updateContentInfo(connection, object, content.getCount(), etag);
                return new DbStorageObject(object.bucket(), key, content.getCount(), contentType, etag, now);
            });
            return UploadResponse.of(key, stored.etag(), stored);
        } catch (IOException e) {
            throw new ObjectStorageException("Error closing the upload stream of " + key, e);
        }
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<DbStorageEntry> retrieve(@NonNull String key) {
        Optional<DbObjectSql.DbObjectRow> row = runtime.transactions().read(connection -> runtime.objects().find(connection, key));
        return row.map(r -> new DbStorageEntry(r, () -> runtime.openStream(r.ref())));
    }

    @Override
    @NonNull
    public Boolean delete(@NonNull String key) {
        return runtime.transactions().write(connection -> {
            Optional<DbObjectSql.DbObjectRow> existing = runtime.objects().find(connection, key);
            if (existing.isEmpty()) {
                return false;
            }
            return deleteVersion(connection, existing.get().ref());
        });
    }

    @Override
    public boolean exists(@NonNull String key) {
        return runtime.transactions().read(connection -> runtime.objects().exists(connection, key));
    }

    @Override
    @NonNull
    public Set<String> listObjects() {
        Set<String> keys = new LinkedHashSet<>();
        String continuationToken = null;
        do {
            ListObjectsResponse response = listObjects(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken));
            keys.addAll(response.getKeys());
            continuationToken = response.getContinuationToken().orElse(null);
        } while (continuationToken != null);
        return keys;
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        int pageSize = request.getPageSize();
        int limit = pageSize == Integer.MAX_VALUE ? pageSize : pageSize + 1;
        List<String> keys = runtime.transactions().read(connection -> runtime.objects().listKeys(
            connection,
            request.getPrefix().orElse(null),
            request.getContinuationToken().orElse(null),
            limit
        ));
        if (keys.size() <= pageSize) {
            return new ListObjectsResponse(keys);
        }
        List<String> page = keys.subList(0, pageSize);
        return new ListObjectsResponse(page, page.get(page.size() - 1));
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        if (sourceKey.equals(destinationKey)) {
            return;
        }
        DbStorageContext context = runtime.context();
        DbContentStore store = runtime.store();
        DbObjectSql objects = runtime.objects();
        runtime.transactions().write(connection -> {
            Optional<DbObjectSql.DbObjectRow> source = objects.find(connection, sourceKey);
            if (source.isEmpty()) {
                return null;
            }
            Optional<DbObjectSql.DbObjectRow> destination = objects.find(connection, destinationKey);
            if (destination.isPresent()) {
                deleteVersion(connection, destination.get().ref());
            }
            DbObjectRef target = objects.copy(connection, source.get(), destinationKey, Instant.now(),
                store.getCopiedColumns(context.mapping()));
            store.copyContent(context, connection, source.get().ref(), target);
            return null;
        });
    }

    /**
     * @return The storage configuration.
     */
    @NonNull
    public DbStorageConfiguration getConfiguration() {
        return runtime.configuration();
    }

    private boolean deleteVersion(Connection connection, DbObjectRef object) throws SQLException {
        runtime.store().deleteContent(runtime.context(), connection, object);
        return runtime.objects().delete(connection, object);
    }
}
