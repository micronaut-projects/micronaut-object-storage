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
import io.micronaut.objectstorage.metadata.StorageContainerMetadata;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for container metadata rows and attributes.
 */
@Singleton
@Internal
public class JdbcStorageContainerMetadataRepository extends AbstractJdbcMetadataRepository implements StorageContainerMetadataRepository {

    public static final String UPSERT_SELECT_ID_SQL = """
        SELECT id
        FROM storage_container_metadata
        WHERE tenant_id = ?
          AND storage_name = ?
          AND logical_container = ?
        """;
    public static final String UPDATE_SQL = """
        UPDATE storage_container_metadata
        SET provider_id = ?,
            provider_namespace = ?,
            provider_container = ?,
            updated_at = ?
        WHERE id = ?
        """;
    public static final String INSERT_SQL = """
        INSERT INTO storage_container_metadata (
            id,
            tenant_id,
            storage_name,
            provider_id,
            logical_container,
            provider_namespace,
            provider_container,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    public static final String DELETE_ATTRS_SQL = "DELETE FROM storage_container_metadata_attr WHERE container_metadata_id = ?";
    public static final String INSERT_ATTR_SQL = """
        INSERT INTO storage_container_metadata_attr (
            container_metadata_id,
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
               created_at,
               updated_at
        FROM storage_container_metadata
        WHERE tenant_id = ?
          AND storage_name = ?
          AND logical_container = ?
        """;
    public static final String FIND_ATTRS_SQL = """
        SELECT attr_key, attr_value
        FROM storage_container_metadata_attr
        WHERE container_metadata_id = ?
        ORDER BY attr_key
        """;

    public JdbcStorageContainerMetadataRepository(@NonNull DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void upsert(@NonNull StorageContainerMetadata metadata, @NonNull Map<String, String> attributes) {
        Objects.requireNonNull(metadata, "metadata");
        Map<String, String> normalizedAttributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        withTransaction(connection -> {
            String id = resolveContainerId(connection, metadata).orElseGet(() -> UUID.randomUUID().toString());
            Instant now = Instant.now();
            if (exists(connection, id)) {
                updateContainer(connection, id, metadata, now);
            } else {
                insertContainer(connection, id, metadata, now);
            }
            replaceAttributes(connection, id, normalizedAttributes);
        });
    }

    @Override
    @NonNull
    public Optional<StorageContainerMetadataRecord> findByTenantAndDescriptor(@NonNull String tenantId,
                                                                               @NonNull StorageDescriptor storageDescriptor) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(storageDescriptor, "storageDescriptor");
        return withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(FIND_SQL)) {
                statement.setString(1, tenantId);
                statement.setString(2, storageDescriptor.getStorageName());
                statement.setString(3, storageDescriptor.getLogicalContainer());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    String id = resultSet.getString("id");
                    StorageDescriptor descriptor = new StorageDescriptor(
                        resultSet.getString("storage_name"),
                        resultSet.getString("provider_id"),
                        resultSet.getString("logical_container"),
                        resultSet.getString("provider_namespace"),
                        resultSet.getString("provider_container")
                    );
                    StorageContainerMetadata metadata = new StorageContainerMetadata(
                        resultSet.getString("tenant_id"),
                        descriptor,
                        instantOf(resultSet.getTimestamp("created_at")),
                        instantOf(resultSet.getTimestamp("updated_at"))
                    );
                    return Optional.of(new StorageContainerMetadataRecord(metadata, findAttributes(connection, id)));
                }
            }
        });
    }

    private static Optional<String> resolveContainerId(java.sql.Connection connection,
                                                       StorageContainerMetadata metadata) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SELECT_ID_SQL)) {
            statement.setString(1, metadata.getTenantId());
            statement.setString(2, metadata.getStorageDescriptor().getStorageName());
            statement.setString(3, metadata.getStorageDescriptor().getLogicalContainer());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty();
            }
        }
    }

    private static boolean exists(java.sql.Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM storage_container_metadata WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void updateContainer(java.sql.Connection connection,
                                        String id,
                                        StorageContainerMetadata metadata,
                                        Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            StorageDescriptor descriptor = metadata.getStorageDescriptor();
            statement.setString(1, descriptor.getProviderId());
            statement.setString(2, descriptor.getProviderNamespace().orElse(null));
            statement.setString(3, descriptor.getProviderContainer());
            statement.setTimestamp(4, timestampOf(now));
            statement.setString(5, id);
            statement.executeUpdate();
        }
    }

    private static void insertContainer(java.sql.Connection connection,
                                        String id,
                                        StorageContainerMetadata metadata,
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
            statement.setTimestamp(8, timestampOf(now));
            statement.setTimestamp(9, timestampOf(now));
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
