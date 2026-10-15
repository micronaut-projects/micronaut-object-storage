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
import io.micronaut.objectstorage.db.internal.DbStorageRuntime;
import io.micronaut.objectstorage.db.internal.JsonMaps;
import io.micronaut.objectstorage.db.spi.DbSqlDialect;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import io.micronaut.objectstorage.metadata.BucketMetadataEntry;
import io.micronaut.objectstorage.metadata.BucketMetadataOperations;
import io.micronaut.objectstorage.metadata.BucketMetadataWrite;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Bucket metadata operations of the database storage. The snapshot is stored as JSON in the bucket row.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachBean(DbStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = DbStorageConfiguration.class)
public class DbBucketMetadataOperations implements BucketMetadataOperations<DbBucket> {

    private static final String BUCKET = DbTableMapping.BUCKET_TABLE;

    private final DbStorageRuntime runtime;

    DbBucketMetadataOperations(@Parameter DbStorageRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @NonNull
    public Optional<BucketMetadataEntry<DbBucket>> retrieve(@NonNull String name) {
        runtime.requireBuiltInTables("Bucket metadata operations");
        DbSqlDialect sql = runtime.context().sql();
        return runtime.transactions().read(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT " + sql.selectJson("METADATA", true) + ", "
                + sql.selectJson("ATTRIBUTES", true) + ", CREATED_AT FROM " + BUCKET + " WHERE BUCKET_NAME = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String metadata = rs.getString(1);
                    String attributes = rs.getString(2);
                    if (metadata == null && attributes == null) {
                        return Optional.empty();
                    }
                    return Optional.of(new BucketMetadataEntry<>(name, JsonMaps.read(metadata), JsonMaps.read(attributes),
                        new DbBucket(name, sql.readTimestamp(rs, 3))));
                }
            }
        });
    }

    @Override
    public void save(@NonNull BucketMetadataWrite write) {
        runtime.requireBuiltInTables("Bucket metadata operations");
        boolean updated = runtime.transactions().write(connection ->
            update(connection, write.name(), JsonMaps.write(write.metadata()), JsonMaps.write(write.attributes())));
        if (!updated) {
            throw new ObjectStorageException("Cannot persist metadata for a missing bucket: " + write.name());
        }
    }

    @Override
    public void delete(@NonNull String name) {
        runtime.requireBuiltInTables("Bucket metadata operations");
        runtime.transactions().write(connection -> update(connection, name, null, null));
    }

    private boolean update(Connection connection, String name, @Nullable String metadata, @Nullable String attributes) throws SQLException {
        DbSqlDialect sql = runtime.context().sql();
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + BUCKET + " SET METADATA = ?, ATTRIBUTES = ? WHERE BUCKET_NAME = ?")) {
            sql.bindJson(ps, 1, metadata);
            sql.bindJson(ps, 2, attributes);
            ps.setString(3, name);
            return ps.executeUpdate() > 0;
        }
    }
}
