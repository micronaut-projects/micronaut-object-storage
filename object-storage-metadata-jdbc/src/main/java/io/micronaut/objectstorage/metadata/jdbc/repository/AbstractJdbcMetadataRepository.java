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
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

@Internal
abstract class AbstractJdbcMetadataRepository {

    private final DataSource dataSource;

    protected AbstractJdbcMetadataRepository(@NonNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    protected <T> T withTransaction(SqlFunction<T> transactionBody) {
        Objects.requireNonNull(transactionBody, "transactionBody");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = transactionBody.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new MetadataJdbcException("JDBC transaction failed", e);
        }
    }

    protected void withTransaction(SqlConsumer transactionBody) {
        withTransaction(connection -> {
            transactionBody.accept(connection);
            return null;
        });
    }

    protected static Timestamp timestampOf(@NonNull Instant instant) {
        return Timestamp.from(Objects.requireNonNull(instant, "instant"));
    }

    @NonNull
    protected static Instant instantOf(Timestamp timestamp) {
        return Objects.requireNonNull(timestamp, "timestamp").toInstant();
    }

    @FunctionalInterface
    protected interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    protected interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
