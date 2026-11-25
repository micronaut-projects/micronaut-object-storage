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
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.db.internal.DbStorageRuntime;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Bucket operations of the database storage. Buckets are rows of the {@code MN_OS_BUCKET} table; deleting a bucket
 * deletes its objects and in-progress multipart uploads. Not available for storages mapped onto their own table.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachBean(DbStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = DbStorageConfiguration.class)
public class DbBucketOperations implements BucketOperations<DbBucket> {

    private static final String BUCKET = DbTableMapping.BUCKET_TABLE;

    private final DbStorageRuntime runtime;

    DbBucketOperations(@Parameter DbStorageRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void create(@NonNull String name) {
        runtime.requireBuiltInTables("Bucket operations");
        DbStorageContext context = runtime.context();
        runtime.transactions().write(connection -> {
            if (find(connection, name).isPresent()) {
                return null;
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + BUCKET + " (BUCKET_NAME, CREATED_AT) VALUES (?, ?)")) {
                ps.setString(1, name);
                context.sql().bindTimestamp(ps, 2, Instant.now());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    @NonNull
    public Optional<BucketEntry<DbBucket>> retrieve(@NonNull String name) {
        runtime.requireBuiltInTables("Bucket operations");
        return runtime.transactions().read(connection -> find(connection, name)).map(bucket -> new BucketEntry<>(name, bucket));
    }

    @Override
    public void delete(@NonNull String name) {
        runtime.requireBuiltInTables("Bucket operations");
        DbStorageContext context = runtime.context();
        runtime.transactions().write(connection -> {
            runtime.store().deleteBucketContent(context, connection, name);
            execute(connection, "DELETE FROM " + DbTableMapping.UPLOAD_PART_TABLE + " WHERE UPLOAD_ID IN (SELECT UPLOAD_ID FROM "
                + DbTableMapping.UPLOAD_TABLE + " WHERE BUCKET = ?)", name);
            execute(connection, "DELETE FROM " + DbTableMapping.UPLOAD_TABLE + " WHERE BUCKET = ?", name);
            execute(connection, "DELETE FROM " + DbTableMapping.OBJECT_TABLE + " WHERE BUCKET = ?", name);
            execute(connection, "DELETE FROM " + BUCKET + " WHERE BUCKET_NAME = ?", name);
            return null;
        });
    }

    private Optional<DbBucket> find(Connection connection, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT CREATED_AT FROM " + BUCKET + " WHERE BUCKET_NAME = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DbBucket(name, runtime.context().sql().readTimestamp(rs, 1)));
            }
        }
    }

    private static void execute(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, parameter);
            ps.executeUpdate();
        }
    }
}
