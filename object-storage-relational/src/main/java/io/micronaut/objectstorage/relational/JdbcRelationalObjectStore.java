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
package io.micronaut.objectstorage.relational;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Internal JDBC-backed relational object store.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
final class JdbcRelationalObjectStore {

    private final DataSource dataSource;
    private final RelationalStorageConfiguration configuration;
    private final String qualifiedTableName;

    JdbcRelationalObjectStore(DataSource dataSource, RelationalStorageConfiguration configuration) {
        this.dataSource = dataSource;
        this.configuration = configuration;
        this.qualifiedTableName = configuration.getSchema()
            .map(schema -> schema + '.' + configuration.getTableName())
            .orElse(configuration.getTableName());
        initializeSchema();
    }

    @NonNull
    RelationalStoredObject upload(@NonNull UploadRequest request) {
        return store(
            request.getKey(),
            request.getContentType().orElse(null),
            request.getMetadata(),
            request::getInputStream
        );
    }

    @NonNull
    Optional<RelationalStoredObject> find(@NonNull String key) {
        String sql = "SELECT etag, content_length, content_type, metadata, updated_at FROM " + qualifiedTableName + " WHERE object_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toStoredKey(key));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                    new RelationalStoredObject(
                        key,
                        resultSet.getString("etag"),
                        resultSet.getLong("content_length"),
                        resultSet.getString("content_type"),
                        deserializeMetadata(resultSet.getString("metadata")),
                        toInstant(resultSet.getTimestamp("updated_at"))
                    )
                );
            }
        } catch (SQLException e) {
            throw new ObjectStorageException("Error retrieving relational object metadata for key [" + key + "]", e);
        }
    }

    boolean exists(@NonNull String key) {
        String sql = "SELECT 1 FROM " + qualifiedTableName + " WHERE object_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toStoredKey(key));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new ObjectStorageException("Error checking relational object existence for key [" + key + "]", e);
        }
    }

    @NonNull
    RelationalStoredObject delete(@NonNull String key) {
        Optional<RelationalStoredObject> existing = find(key);
        String sql = "DELETE FROM " + qualifiedTableName + " WHERE object_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toStoredKey(key));
            statement.executeUpdate();
            return existing.orElseGet(() -> new RelationalStoredObject(key, null, null, null, Map.of(), null));
        } catch (SQLException e) {
            throw new ObjectStorageException("Error deleting relational object for key [" + key + "]", e);
        }
    }

    @NonNull
    ListObjectsResponse list(@NonNull ListObjectsRequest request) {
        StringBuilder sql = new StringBuilder("SELECT object_key FROM ")
            .append(qualifiedTableName)
            .append(" WHERE 1 = 1");
        List<Object> arguments = new ArrayList<>(3);
        String prefix = toStoredPrefix(request.getPrefix().orElse(null));
        if (prefix != null) {
            sql.append(" AND object_key LIKE ? ESCAPE '\\'");
            arguments.add(toLikePattern(prefix));
        }
        String continuationToken = request.getContinuationToken().map(this::toStoredKey).orElse(null);
        if (continuationToken != null) {
            sql.append(" AND object_key > ?");
            arguments.add(continuationToken);
        }
        int fetchLimit = fetchLimit(request.getPageSize());
        sql.append(" ORDER BY object_key ASC LIMIT ?");
        arguments.add(fetchLimit);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindArguments(statement, arguments);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> storedKeys = new ArrayList<>();
                while (resultSet.next()) {
                    storedKeys.add(resultSet.getString(1));
                }
                if (storedKeys.isEmpty()) {
                    return new ListObjectsResponse(List.of());
                }
                int pageSize = request.getPageSize();
                List<String> exposedKeys = storedKeys.stream()
                    .limit(pageSize)
                    .map(this::toExternalKey)
                    .toList();
                String nextContinuationToken = storedKeys.size() > pageSize
                    ? exposedKeys.get(exposedKeys.size() - 1)
                    : null;
                return new ListObjectsResponse(exposedKeys, nextContinuationToken);
            }
        } catch (SQLException e) {
            throw new ObjectStorageException("Error listing relational objects", e);
        }
    }

    void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        find(sourceKey).ifPresent(source -> store(
            destinationKey,
            source.contentType(),
            source.metadata(),
            () -> openInputStream(sourceKey)
        ));
    }

    @NonNull
    InputStream openInputStream(@NonNull String key) {
        String sql = "SELECT object_content FROM " + qualifiedTableName + " WHERE object_key = ?";
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(sql);
            statement.setString(1, toStoredKey(key));
            resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                throw new ObjectStorageException("No relational object found for key [" + key + "]");
            }
            InputStream inputStream = resultSet.getBinaryStream(1);
            if (inputStream == null) {
                throw new ObjectStorageException("No relational payload stream available for key [" + key + "]");
            }
            ManagedJdbcInputStream managedInputStream = new ManagedJdbcInputStream(inputStream, resultSet, statement, connection);
            resultSet = null;
            statement = null;
            connection = null;
            return managedInputStream;
        } catch (SQLException e) {
            throw new ObjectStorageException("Error opening relational object stream for key [" + key + "]", e);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    @NonNull
    private RelationalStoredObject store(@NonNull String key,
                                         @Nullable String contentType,
                                         @NonNull Map<String, String> metadata,
                                         @NonNull InputStreamSupplier inputStreamSupplier) {
        Path tempFile = createTempFile();
        Instant updatedAt = Instant.now();
        String eTag;
        long contentLength;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            contentLength = copyToTempFile(tempFile, inputStreamSupplier.open(), digest);
            eTag = HexFormat.of().formatHex(digest.digest());
            writeRow(key, contentType, metadata, tempFile, contentLength, eTag, updatedAt);
            return new RelationalStoredObject(key, eTag, contentLength, contentType, metadata, updatedAt);
        } catch (IOException | SQLException e) {
            throw new ObjectStorageException("Error storing relational object for key [" + key + "]", e);
        } catch (NoSuchAlgorithmException e) {
            throw new ObjectStorageException("SHA-256 message digest is not available", e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Nothing to do.
            }
        }
    }

    private void writeRow(@NonNull String key,
                          @Nullable String contentType,
                          @NonNull Map<String, String> metadata,
                          @NonNull Path tempFile,
                          long contentLength,
                          @NonNull String eTag,
                          @NonNull Instant updatedAt) throws SQLException, IOException {
        String deleteSql = "DELETE FROM " + qualifiedTableName + " WHERE object_key = ?";
        String insertSql = "INSERT INTO " + qualifiedTableName
            + " (object_key, etag, content_length, content_type, metadata, object_content, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql);
                 PreparedStatement insertStatement = connection.prepareStatement(insertSql);
                 InputStream payload = Files.newInputStream(tempFile)) {
                deleteStatement.setString(1, toStoredKey(key));
                deleteStatement.executeUpdate();

                insertStatement.setString(1, toStoredKey(key));
                insertStatement.setString(2, eTag);
                insertStatement.setLong(3, contentLength);
                if (contentType == null || contentType.isBlank()) {
                    insertStatement.setNull(4, Types.VARCHAR);
                } else {
                    insertStatement.setString(4, contentType);
                }
                String metadataValue = serializeMetadata(metadata);
                if (metadataValue == null) {
                    insertStatement.setNull(5, Types.CLOB);
                } else {
                    insertStatement.setString(5, metadataValue);
                }
                insertStatement.setBinaryStream(6, payload, contentLength);
                insertStatement.setTimestamp(7, Timestamp.from(updatedAt));
                insertStatement.executeUpdate();
                connection.commit();
            } catch (SQLException | IOException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            String metadataColumnType = metadataColumnType(connection);
            String payloadColumnType = payloadColumnType(connection);
            if (configuration.getSchema().isPresent()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + configuration.getSchema().orElseThrow());
            }
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " + qualifiedTableName + " ("
                    + "object_key VARCHAR(1024) PRIMARY KEY, "
                    + "etag VARCHAR(128) NOT NULL, "
                    + "content_length BIGINT NOT NULL, "
                    + "content_type VARCHAR(255), "
                    + "metadata " + metadataColumnType + ", "
                    + "object_content " + payloadColumnType + " NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL)"
            );
        } catch (SQLException e) {
            throw new ObjectStorageException("Error initializing relational object storage schema", e);
        }
    }

    @NonNull
    private static String metadataColumnType(@NonNull Connection connection) throws SQLException {
        return usesPostgreSqlLargeObjectTypes(connection) ? "TEXT" : "CLOB";
    }

    @NonNull
    private static String payloadColumnType(@NonNull Connection connection) throws SQLException {
        return usesPostgreSqlLargeObjectTypes(connection) ? "BYTEA" : "BLOB";
    }

    private static boolean usesPostgreSqlLargeObjectTypes(@NonNull Connection connection) throws SQLException {
        String databaseProductName = connection.getMetaData().getDatabaseProductName();
        return "PostgreSQL".equalsIgnoreCase(databaseProductName) || "H2".equalsIgnoreCase(databaseProductName);
    }

    @NonNull
    private String toStoredKey(@NonNull String key) {
        return configuration.getKeyPrefix()
            .map(prefix -> prefix + key)
            .orElse(key);
    }

    @Nullable
    private String toStoredPrefix(@Nullable String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return configuration.getKeyPrefix().orElse(null);
        }
        return configuration.getKeyPrefix()
            .map(configuredPrefix -> configuredPrefix + prefix)
            .orElse(prefix);
    }

    @NonNull
    private String toExternalKey(@NonNull String storedKey) {
        return configuration.getKeyPrefix()
            .filter(storedKey::startsWith)
            .map(prefix -> storedKey.substring(prefix.length()))
            .orElse(storedKey);
    }

    @NonNull
    private static Path createTempFile() {
        try {
            return Files.createTempFile("micronaut-object-storage-relational-", ".bin");
        } catch (IOException e) {
            throw new ObjectStorageException("Error creating temporary file for relational object storage", e);
        }
    }

    private static long copyToTempFile(@NonNull Path tempFile,
                                       @NonNull InputStream inputStream,
                                       @NonNull MessageDigest digest) throws IOException {
        try (InputStream source = inputStream;
             OutputStream outputStream = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[8_192];
            long count = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                outputStream.write(buffer, 0, read);
                count += read;
            }
            return count;
        }
    }

    private static void bindArguments(@NonNull PreparedStatement statement, @NonNull List<Object> arguments) throws SQLException {
        for (int index = 0; index < arguments.size(); index++) {
            statement.setObject(index + 1, arguments.get(index));
        }
    }

    @NonNull
    private static String toLikePattern(@NonNull String prefix) {
        return escapeLikePattern(prefix) + '%';
    }

    @NonNull
    private static String escapeLikePattern(@NonNull String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private static int fetchLimit(int pageSize) {
        return pageSize == Integer.MAX_VALUE ? Integer.MAX_VALUE : pageSize + 1;
    }

    @Nullable
    private static String serializeMetadata(@NonNull Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return null;
        }
        Properties properties = new Properties();
        properties.putAll(metadata);
        try {
            StringWriter writer = new StringWriter();
            properties.store(writer, null);
            return writer.toString();
        } catch (IOException e) {
            throw new ObjectStorageException("Error serializing relational object metadata", e);
        }
    }

    @NonNull
    private static Map<String, String> deserializeMetadata(@Nullable String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(metadata));
        } catch (IOException e) {
            throw new ObjectStorageException("Error deserializing relational object metadata", e);
        }
        Map<String, String> values = new LinkedHashMap<>(properties.size());
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return values;
    }

    @Nullable
    private static Instant toInstant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Nothing to do.
        }
    }

    @FunctionalInterface
    interface InputStreamSupplier {
        @NonNull
        InputStream open() throws IOException;
    }

    private static final class ManagedJdbcInputStream extends FilterInputStream {

        private final ResultSet resultSet;
        private final Statement statement;
        private final Connection connection;
        private boolean closed;

        private ManagedJdbcInputStream(InputStream inputStream,
                                       ResultSet resultSet,
                                       Statement statement,
                                       Connection connection) {
            super(inputStream);
            this.resultSet = resultSet;
            this.statement = statement;
            this.connection = connection;
        }

        @Override
        public int read() throws IOException {
            int read = super.read();
            closeOnEndOfStream(read);
            return read;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int read = super.read(buffer, off, len);
            closeOnEndOfStream(read);
            return read;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException thrown = null;
            try {
                super.close();
            } catch (IOException e) {
                thrown = e;
            } finally {
                closeQuietly(resultSet);
                closeQuietly(statement);
                closeQuietly(connection);
            }
            if (thrown != null) {
                throw thrown;
            }
        }

        private void closeOnEndOfStream(int read) throws IOException {
            if (read == -1) {
                close();
            }
        }
    }
}
