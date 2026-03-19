/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.metadata.jdbc.repository;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState;
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageObjectMetadata;
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for object metadata rows and attributes.
 */
@Singleton
@Internal
public class JdbcStorageObjectMetadataRepository extends AbstractJdbcMetadataRepository implements StorageObjectMetadataRepository {

    public static final String SELECT_ID_SQL = """
        SELECT id
        FROM storage_object_metadata
        WHERE tenant_id = ?
          AND storage_name = ?
          AND logical_container = ?
          AND object_key_hash = ?
        """;
    public static final String UPDATE_SQL = """
        UPDATE storage_object_metadata
        SET provider_id = ?,
            provider_namespace = ?,
            provider_container = ?,
            object_key = ?,
            content_type = ?,
            content_length = ?,
            etag = ?,
            provider_version_id = ?,
            checksum = ?,
            reconciliation_state = ?,
            last_error_summary = ?,
            updated_at = ?
        WHERE id = ?
        """;
    public static final String INSERT_SQL = """
        INSERT INTO storage_object_metadata (
            id,
            tenant_id,
            storage_name,
            provider_id,
            logical_container,
            provider_namespace,
            provider_container,
            object_key,
            object_key_hash,
            content_type,
            content_length,
            etag,
            provider_version_id,
            checksum,
            reconciliation_state,
            last_error_summary,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    public static final String DELETE_ATTRS_SQL = "DELETE FROM storage_object_metadata_attr WHERE object_metadata_id = ?";
    public static final String INSERT_ATTR_SQL = """
        INSERT INTO storage_object_metadata_attr (
            object_metadata_id,
            attr_key,
            attr_value
        ) VALUES (?, ?, ?)
        """;
    public static final String FIND_SQL = """
        SELECT id,
               tenant_id,
               storage_name,
               provider_id,
               logical_container,
               provider_namespace,
               provider_container,
               object_key,
               object_key_hash,
               content_type,
               content_length,
               etag,
               provider_version_id,
               checksum,
               reconciliation_state,
               last_error_summary,
               created_at,
               updated_at
        FROM storage_object_metadata
        WHERE tenant_id = ?
          AND storage_name = ?
          AND logical_container = ?
          AND object_key_hash = ?
        """;
    public static final String LIST_SQL = """
        SELECT id,
               tenant_id,
               storage_name,
               provider_id,
               logical_container,
               provider_namespace,
               provider_container,
               object_key,
               object_key_hash,
               content_type,
               content_length,
               etag,
               provider_version_id,
               checksum,
               reconciliation_state,
               last_error_summary,
               created_at,
               updated_at
        FROM storage_object_metadata
        WHERE tenant_id = ?
          AND (? IS NULL OR storage_name = ?)
          AND (? IS NULL OR logical_container = ?)
          AND (? IS NULL OR object_key LIKE ?)
        ORDER BY object_key
        """;
    public static final String FIND_ATTRS_SQL = """
        SELECT attr_key, attr_value
        FROM storage_object_metadata_attr
        WHERE object_metadata_id = ?
        ORDER BY attr_key
        """;
    public static final String DELETE_BY_SCOPE_SQL = """
        DELETE FROM storage_object_metadata
        WHERE tenant_id = ?
          AND storage_name = ?
          AND logical_container = ?
          AND object_key_hash = ?
        """;

    public JdbcStorageObjectMetadataRepository(@NonNull DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void upsert(@NonNull StorageObjectMetadata metadata, @NonNull Map<String, String> attributes) {
        Objects.requireNonNull(metadata, "metadata");
        Map<String, String> normalizedAttributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        withTransaction(connection -> {
            String id = resolveObjectId(connection, metadata).orElseGet(() -> UUID.randomUUID().toString());
            Instant now = Instant.now();
            if (exists(connection, id)) {
                updateObject(connection, id, metadata, now);
            } else {
                insertObject(connection, id, metadata, now);
            }
            replaceAttributes(connection, id, normalizedAttributes);
        });
    }

    @Override
    @NonNull
    public Optional<StorageObjectMetadataRecord> findByTenantAndObjectHash(@NonNull String tenantId,
                                                                            @NonNull StorageDescriptor storageDescriptor,
                                                                            @NonNull String objectKeyHash) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        Objects.requireNonNull(objectKeyHash, "objectKeyHash");
        return withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(FIND_SQL)) {
                statement.setString(1, tenantId);
                statement.setString(2, storageDescriptor.getStorageName());
                statement.setString(3, storageDescriptor.getLogicalContainer());
                statement.setString(4, objectKeyHash);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    String id = resultSet.getString("id");
                    return Optional.of(new StorageObjectMetadataRecord(readMetadata(resultSet), findAttributes(connection, id)));
                }
            }
        });
    }

    @Override
    @NonNull
    public List<StorageObjectMetadataRecord> listByTenantAndQuery(@NonNull String tenantId,
                                                                   @NonNull StorageObjectMetadataQuery query) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        return withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(LIST_SQL)) {
                String storageName = query.getStorageName().orElse(null);
                String logicalContainer = query.getLogicalContainer().orElse(null);
                String objectKeyPrefix = query.getObjectKeyPrefix().orElse(null);
                statement.setString(1, tenantId);
                statement.setString(2, storageName);
                statement.setString(3, storageName);
                statement.setString(4, logicalContainer);
                statement.setString(5, logicalContainer);
                statement.setString(6, objectKeyPrefix);
                statement.setString(7, objectKeyPrefix == null ? null : objectKeyPrefix + "%");
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<StorageObjectMetadataRecord> results = new ArrayList<>();
                    while (resultSet.next()) {
                        String id = resultSet.getString("id");
                        results.add(new StorageObjectMetadataRecord(readMetadata(resultSet), findAttributes(connection, id)));
                    }
                    return List.copyOf(results);
                }
            }
        });
    }

    @Override
    public void deleteByTenantAndObjectHash(@NonNull String tenantId,
                                            @NonNull StorageDescriptor storageDescriptor,
                                            @NonNull String objectKeyHash) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        Objects.requireNonNull(objectKeyHash, "objectKeyHash");
        withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_BY_SCOPE_SQL)) {
                statement.setString(1, tenantId);
                statement.setString(2, storageDescriptor.getStorageName());
                statement.setString(3, storageDescriptor.getLogicalContainer());
                statement.setString(4, objectKeyHash);
                statement.executeUpdate();
            }
        });
    }

    private static Optional<String> resolveObjectId(java.sql.Connection connection,
                                                    StorageObjectMetadata metadata) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ID_SQL)) {
            statement.setString(1, metadata.getTenantId());
            statement.setString(2, metadata.getStorageName());
            statement.setString(3, metadata.getLogicalContainer());
            statement.setString(4, metadata.getObjectKeyHash());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty();
            }
        }
    }

    private static boolean exists(java.sql.Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM storage_object_metadata WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void updateObject(java.sql.Connection connection,
                                     String id,
                                     StorageObjectMetadata metadata,
                                     Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            StorageDescriptor descriptor = metadata.getStorageDescriptor();
            statement.setString(1, descriptor.getProviderId());
            statement.setString(2, descriptor.getProviderNamespace().orElse(null));
            statement.setString(3, descriptor.getProviderContainer());
            statement.setString(4, metadata.getObjectKey());
            statement.setString(5, metadata.getContentType().orElse(null));
            if (metadata.getContentLength().isPresent()) {
                statement.setLong(6, metadata.getContentLength().getAsLong());
            } else {
                statement.setObject(6, null);
            }
            statement.setString(7, metadata.getEtag().orElse(null));
            statement.setString(8, metadata.getProviderVersionId().orElse(null));
            statement.setString(9, metadata.getChecksum().orElse(null));
            statement.setString(10, metadata.getReconciliationStatus().name());
            statement.setString(11, metadata.getLastErrorSummary().orElse(null));
            statement.setTimestamp(12, timestampOf(now));
            statement.setString(13, id);
            statement.executeUpdate();
        }
    }

    private static void insertObject(java.sql.Connection connection,
                                     String id,
                                     StorageObjectMetadata metadata,
                                     Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            StorageDescriptor descriptor = metadata.getStorageDescriptor();
            statement.setString(1, id);
            statement.setString(2, metadata.getTenantId());
            statement.setString(3, descriptor.getStorageName());
            statement.setString(4, descriptor.getProviderId());
            statement.setString(5, descriptor.getLogicalContainer());
            statement.setString(6, descriptor.getProviderNamespace().orElse(null));
            statement.setString(7, descriptor.getProviderContainer());
            statement.setString(8, metadata.getObjectKey());
            statement.setString(9, metadata.getObjectKeyHash());
            statement.setString(10, metadata.getContentType().orElse(null));
            if (metadata.getContentLength().isPresent()) {
                statement.setLong(11, metadata.getContentLength().getAsLong());
            } else {
                statement.setObject(11, null);
            }
            statement.setString(12, metadata.getEtag().orElse(null));
            statement.setString(13, metadata.getProviderVersionId().orElse(null));
            statement.setString(14, metadata.getChecksum().orElse(null));
            statement.setString(15, metadata.getReconciliationStatus().name());
            statement.setString(16, metadata.getLastErrorSummary().orElse(null));
            statement.setTimestamp(17, timestampOf(now));
            statement.setTimestamp(18, timestampOf(now));
            statement.executeUpdate();
        }
    }

    private static void replaceAttributes(java.sql.Connection connection,
                                          String id,
                                          Map<String, String> attributes) throws SQLException {
        try (PreparedStatement deleteStatement = connection.prepareStatement(DELETE_ATTRS_SQL)) {
            deleteStatement.setString(1, id);
            deleteStatement.executeUpdate();
        }
        if (attributes.isEmpty()) {
            return;
        }
        try (PreparedStatement insertStatement = connection.prepareStatement(INSERT_ATTR_SQL)) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                insertStatement.setString(1, id);
                insertStatement.setString(2, entry.getKey());
                insertStatement.setString(3, entry.getValue());
                insertStatement.addBatch();
            }
            insertStatement.executeBatch();
        }
    }

    private static StorageObjectMetadata readMetadata(ResultSet resultSet) throws SQLException {
        StorageDescriptor descriptor = new StorageDescriptor(
            resultSet.getString("storage_name"),
            resultSet.getString("provider_id"),
            resultSet.getString("logical_container"),
            resultSet.getString("provider_namespace"),
            resultSet.getString("provider_container")
        );
        ObjectMetadataSyncStatus syncStatus = new ObjectMetadataSyncStatus(
            ObjectMetadataReconciliationState.valueOf(resultSet.getString("reconciliation_state")),
            resultSet.getString("last_error_summary")
        );
        Long contentLength = resultSet.getLong("content_length");
        if (resultSet.wasNull()) {
            contentLength = null;
        }
        return new StorageObjectMetadata(
            resultSet.getString("tenant_id"),
            descriptor,
            resultSet.getString("object_key"),
            resultSet.getString("object_key_hash"),
            resultSet.getString("content_type"),
            contentLength,
            resultSet.getString("etag"),
            resultSet.getString("provider_version_id"),
            resultSet.getString("checksum"),
            instantOf(resultSet.getTimestamp("created_at")),
            instantOf(resultSet.getTimestamp("updated_at")),
            syncStatus
        );
    }

    private static Map<String, String> findAttributes(java.sql.Connection connection, String id) throws SQLException {
        Map<String, String> attributes = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(FIND_ATTRS_SQL)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    attributes.put(resultSet.getString("attr_key"), resultSet.getString("attr_value"));
                }
            }
        }
        return attributes;
    }
}
