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
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for durable metadata outbox entries.
 */
@Singleton
@Internal
public class JdbcStorageMetadataOutboxRepository extends AbstractJdbcMetadataRepository implements StorageMetadataOutboxRepository {

    static final String INSERT_SQL = """
        INSERT INTO storage_metadata_outbox (
            id,
            tenant_id,
            storage_name,
            logical_container,
            object_key,
            object_key_hash,
            destination_object_key,
            destination_object_key_hash,
            operation_type,
            reconciliation_id,
            payload_version,
            status,
            attempt_count,
            next_attempt_at,
            last_error_summary,
            idempotency_key,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    static final String FIND_DUE_SQL = """
        SELECT id,
               tenant_id,
               storage_name,
               logical_container,
               object_key,
               object_key_hash,
               destination_object_key,
               destination_object_key_hash,
               operation_type,
               reconciliation_id,
               payload_version,
               status,
               attempt_count,
               next_attempt_at,
               last_error_summary,
               idempotency_key,
               created_at,
               updated_at
        FROM storage_metadata_outbox
        WHERE status IN ('PENDING', 'FAILED', 'PROCESSING')
          AND next_attempt_at <= ?
        ORDER BY next_attempt_at ASC, created_at ASC
        FETCH FIRST ? ROWS ONLY
        """;
    static final String UPDATE_PROCESSING_SQL = """
        UPDATE storage_metadata_outbox
        SET status = 'PROCESSING',
            updated_at = ?
        WHERE id = ?
          AND status IN ('PENDING', 'FAILED', 'PROCESSING')
          AND next_attempt_at <= ?
        """;
    static final String UPDATE_SUCCEEDED_SQL = """
        UPDATE storage_metadata_outbox
        SET status = 'SUCCEEDED',
            last_error_summary = NULL,
            updated_at = ?
        WHERE id = ?
        """;
    static final String UPDATE_RETRY_OR_DEAD_LETTER_SQL = """
        UPDATE storage_metadata_outbox
        SET status = ?,
            attempt_count = ?,
            next_attempt_at = ?,
            last_error_summary = ?,
            updated_at = ?
        WHERE id = ?
        """;
    static final String FIND_BY_ID_SQL = """
        SELECT id,
               tenant_id,
               storage_name,
               logical_container,
               object_key,
               object_key_hash,
               destination_object_key,
               destination_object_key_hash,
               operation_type,
               reconciliation_id,
               payload_version,
               status,
               attempt_count,
               next_attempt_at,
               last_error_summary,
               idempotency_key,
               created_at,
               updated_at
        FROM storage_metadata_outbox
        WHERE id = ?
        """;
    static final String FIND_BY_IDEMPOTENCY_KEY_SQL = """
        SELECT id,
               tenant_id,
               storage_name,
               logical_container,
               object_key,
               object_key_hash,
               destination_object_key,
               destination_object_key_hash,
               operation_type,
               reconciliation_id,
               payload_version,
               status,
               attempt_count,
               next_attempt_at,
               last_error_summary,
               idempotency_key,
               created_at,
               updated_at
        FROM storage_metadata_outbox
        WHERE idempotency_key = ?
        """;

    public JdbcStorageMetadataOutboxRepository(@NonNull DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void enqueue(@NonNull StorageMetadataOutboxRecord outboxRecord) {
        Objects.requireNonNull(outboxRecord, "outboxRecord");
        withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                statement.setString(1, outboxRecord.getId());
                statement.setString(2, outboxRecord.getTenantId());
                statement.setString(3, outboxRecord.getStorageName());
                statement.setString(4, outboxRecord.getLogicalContainer());
                statement.setString(5, outboxRecord.getObjectKey().orElse(null));
                statement.setString(6, outboxRecord.getObjectKeyHash().orElse(null));
                statement.setString(7, outboxRecord.getDestinationObjectKey().orElse(null));
                statement.setString(8, outboxRecord.getDestinationObjectKeyHash().orElse(null));
                statement.setString(9, outboxRecord.getOperationType().name());
                statement.setString(10, outboxRecord.getReconciliationId().orElse(null));
                statement.setInt(11, outboxRecord.getPayloadVersion());
                statement.setString(12, outboxRecord.getStatus().name());
                statement.setInt(13, outboxRecord.getAttemptCount());
                statement.setTimestamp(14, timestampOf(outboxRecord.getNextAttemptAt()));
                statement.setString(15, outboxRecord.getLastErrorSummary().orElse(null));
                statement.setString(16, outboxRecord.getIdempotencyKey());
                statement.setTimestamp(17, timestampOf(outboxRecord.getCreatedAt()));
                statement.setTimestamp(18, timestampOf(outboxRecord.getUpdatedAt()));
                statement.executeUpdate();
            }
        });
    }

    @Override
    public boolean enqueueIfAbsent(@NonNull StorageMetadataOutboxRecord outboxRecord) {
        Objects.requireNonNull(outboxRecord, "outboxRecord");
        return withTransaction(connection -> {
            try {
                try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                    statement.setString(1, outboxRecord.getId());
                    statement.setString(2, outboxRecord.getTenantId());
                    statement.setString(3, outboxRecord.getStorageName());
                    statement.setString(4, outboxRecord.getLogicalContainer());
                    statement.setString(5, outboxRecord.getObjectKey().orElse(null));
                    statement.setString(6, outboxRecord.getObjectKeyHash().orElse(null));
                    statement.setString(7, outboxRecord.getDestinationObjectKey().orElse(null));
                    statement.setString(8, outboxRecord.getDestinationObjectKeyHash().orElse(null));
                    statement.setString(9, outboxRecord.getOperationType().name());
                    statement.setString(10, outboxRecord.getReconciliationId().orElse(null));
                    statement.setInt(11, outboxRecord.getPayloadVersion());
                    statement.setString(12, outboxRecord.getStatus().name());
                    statement.setInt(13, outboxRecord.getAttemptCount());
                    statement.setTimestamp(14, timestampOf(outboxRecord.getNextAttemptAt()));
                    statement.setString(15, outboxRecord.getLastErrorSummary().orElse(null));
                    statement.setString(16, outboxRecord.getIdempotencyKey());
                    statement.setTimestamp(17, timestampOf(outboxRecord.getCreatedAt()));
                    statement.setTimestamp(18, timestampOf(outboxRecord.getUpdatedAt()));
                    statement.executeUpdate();
                }
                return true;
            } catch (SQLIntegrityConstraintViolationException ignored) {
                return false;
            }
        });
    }

    @Override
    @NonNull
    public List<StorageMetadataOutboxRecord> findDueEntries(@NonNull Instant asOf, int limit) {
        Instant boundedAsOf = Objects.requireNonNull(asOf, "asOf");
        if (limit <= 0) {
            return List.of();
        }
        return withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(FIND_DUE_SQL)) {
                statement.setTimestamp(1, timestampOf(boundedAsOf));
                statement.setInt(2, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<StorageMetadataOutboxRecord> records = new ArrayList<>();
                    while (resultSet.next()) {
                        records.add(readOutboxRecord(resultSet));
                    }
                    return List.copyOf(records);
                }
            }
        });
    }

    @Override
    public boolean markProcessing(@NonNull String id,
                                  @NonNull Instant asOf,
                                  @NonNull Instant updatedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_PROCESSING_SQL)) {
                statement.setTimestamp(1, timestampOf(updatedAt));
                statement.setString(2, id);
                statement.setTimestamp(3, timestampOf(asOf));
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public void markSucceeded(@NonNull String id,
                              @NonNull Instant updatedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(updatedAt, "updatedAt");
        withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SUCCEEDED_SQL)) {
                statement.setTimestamp(1, timestampOf(updatedAt));
                statement.setString(2, id);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void markRetryOrDeadLetter(@NonNull String id,
                                      int attemptCount,
                                      @NonNull Instant nextAttemptAt,
                                      @NonNull StorageMetadataOutboxStatus status,
                                      @NonNull String lastErrorSummary,
                                      @NonNull Instant updatedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(lastErrorSummary, "lastErrorSummary");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (status != StorageMetadataOutboxStatus.FAILED && status != StorageMetadataOutboxStatus.DEAD_LETTER) {
            throw new IllegalArgumentException("status must be FAILED or DEAD_LETTER");
        }
        withTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_RETRY_OR_DEAD_LETTER_SQL)) {
                statement.setString(1, status.name());
                statement.setInt(2, attemptCount);
                statement.setTimestamp(3, timestampOf(nextAttemptAt));
                statement.setString(4, lastErrorSummary);
                statement.setTimestamp(5, timestampOf(updatedAt));
                statement.setString(6, id);
                statement.executeUpdate();
            }
        });
    }

    @Override
    @NonNull
    public Optional<StorageMetadataOutboxRecord> findById(@NonNull String id) {
        Objects.requireNonNull(id, "id");
        return withTransaction((SqlFunction<Optional<StorageMetadataOutboxRecord>>) connection ->
            findSingle(connection.prepareStatement(FIND_BY_ID_SQL), id));
    }

    @Override
    @NonNull
    public Optional<StorageMetadataOutboxRecord> findByIdempotencyKey(@NonNull String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return withTransaction((SqlFunction<Optional<StorageMetadataOutboxRecord>>) connection ->
            findSingle(connection.prepareStatement(FIND_BY_IDEMPOTENCY_KEY_SQL), idempotencyKey));
    }

    private static Optional<StorageMetadataOutboxRecord> findSingle(PreparedStatement statement,
                                                                    String predicate) throws java.sql.SQLException {
        try (statement) {
            statement.setString(1, predicate);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readOutboxRecord(resultSet));
            }
        }
    }

    private static StorageMetadataOutboxRecord readOutboxRecord(ResultSet resultSet) throws java.sql.SQLException {
        return StorageMetadataOutboxRecord.builder()
            .id(resultSet.getString("id"))
            .tenantId(resultSet.getString("tenant_id"))
            .storageName(resultSet.getString("storage_name"))
            .logicalContainer(resultSet.getString("logical_container"))
            .objectKey(resultSet.getString("object_key"))
            .objectKeyHash(resultSet.getString("object_key_hash"))
            .destinationObjectKey(resultSet.getString("destination_object_key"))
            .destinationObjectKeyHash(resultSet.getString("destination_object_key_hash"))
            .operationType(io.micronaut.objectstorage.metadata.ObjectStorageOperationType.valueOf(resultSet.getString("operation_type")))
            .reconciliationId(resultSet.getString("reconciliation_id"))
            .payloadVersion(resultSet.getInt("payload_version"))
            .status(StorageMetadataOutboxStatus.valueOf(resultSet.getString("status")))
            .attemptCount(resultSet.getInt("attempt_count"))
            .nextAttemptAt(instantOf(resultSet.getTimestamp("next_attempt_at")))
            .lastErrorSummary(resultSet.getString("last_error_summary"))
            .idempotencyKey(resultSet.getString("idempotency_key"))
            .createdAt(instantOf(resultSet.getTimestamp("created_at")))
            .updatedAt(instantOf(resultSet.getTimestamp("updated_at")))
            .build();
    }
}
