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
package io.micronaut.objectstorage.db.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.TransactionOperations;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs storage work in transactions of the storage's datasource, applying the configured propagation to writes.
 *
 * <p>A chunked or LOB write is several statements, so a write always runs in a transaction: when the propagation
 * would run without one ({@code SUPPORTS} or {@code NOT_SUPPORTED} with no caller transaction, or {@code NEVER}),
 * the storage starts its own short transaction instead, so an object is never visible half written. Reads join the
 * caller's transaction when there is one, so they see its uncommitted uploads, and use a short transaction
 * otherwise.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class DbTransactions {

    private final TransactionOperations<Connection> operations;
    private final Propagation propagation;
    private final String storageName;
    private final String sqlErrorHint;

    /**
     * @param operations The transaction operations of the datasource.
     * @param propagation The configured propagation of writes.
     * @param storageName The storage name, for error messages.
     * @param sqlErrorHint Appended to messages of SQL errors, for example to describe a table mapping.
     */
    public DbTransactions(@NonNull TransactionOperations<Connection> operations,
                          @NonNull Propagation propagation,
                          @NonNull String storageName,
                          @NonNull String sqlErrorHint) {
        this.operations = operations;
        this.propagation = propagation;
        this.storageName = storageName;
        this.sqlErrorHint = sqlErrorHint;
    }

    /**
     * @return Whether a transaction of the datasource is active on the current thread.
     */
    public boolean isActive() {
        return operations.findTransactionStatus().map(status -> !status.isCompleted()).orElse(false);
    }

    /**
     * Runs write work with the configured propagation.
     *
     * @param work The work.
     * @param <R> The result type.
     * @return The result.
     */
    public <R> R write(@NonNull Work<R> work) {
        Propagation effective = switch (propagation) {
            case SUPPORTS -> Propagation.REQUIRED;
            case NOT_SUPPORTED -> Propagation.REQUIRES_NEW;
            case NEVER -> {
                if (isActive()) {
                    throw new ObjectStorageException("Database storage '" + storageName
                        + "' uses transaction propagation NEVER but a transaction is active");
                }
                yield Propagation.REQUIRED;
            }
            default -> propagation;
        };
        return run(TransactionDefinition.of(effective), work);
    }

    /**
     * Runs read work, joining the caller's transaction if there is one.
     *
     * @param work The work.
     * @param <R> The result type.
     * @return The result.
     */
    public <R> R read(@NonNull Work<R> work) {
        return run(TransactionDefinition.DEFAULT, work);
    }

    /**
     * Runs work in a new transaction, suspending the caller's transaction if there is one.
     *
     * @param work The work.
     * @param <R> The result type.
     * @return The result.
     */
    public <R> R requiresNew(@NonNull Work<R> work) {
        return run(TransactionDefinition.of(Propagation.REQUIRES_NEW), work);
    }

    private <R> R run(TransactionDefinition definition, Work<R> work) {
        try {
            return operations.execute(definition, status -> {
                try {
                    return work.run(status.getConnection());
                } catch (SQLException e) {
                    throw new WorkException(e);
                } catch (IOException e) {
                    throw new WorkException(e);
                }
            });
        } catch (WorkException e) {
            throw e.unwrap(storageName, sqlErrorHint);
        } catch (ObjectStorageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ObjectStorageException("Database storage '" + storageName + "' failed: " + e.getMessage(), e);
        }
    }

    /**
     * Work on a connection.
     *
     * @param <R> The result type.
     */
    @FunctionalInterface
    public interface Work<R> {

        /**
         * @param connection The connection of the transaction.
         * @return The result.
         * @throws SQLException If a statement fails.
         * @throws IOException If reading or writing content fails.
         */
        R run(@NonNull Connection connection) throws SQLException, IOException;
    }

    /**
     * Carries a checked exception out of the transaction callback, so that the transaction rolls back.
     */
    private static final class WorkException extends RuntimeException {

        private WorkException(Exception cause) {
            super(cause.getMessage(), cause, false, false);
        }

        private ObjectStorageException unwrap(String storageName, String sqlErrorHint) {
            Throwable cause = getCause();
            if (cause instanceof DigestingInputStream.ObjectTooLargeException tooLarge) {
                return new ObjectStorageException("Database storage '" + storageName + "': " + tooLarge.getMessage(), tooLarge);
            }
            String hint = cause instanceof SQLException ? sqlErrorHint : "";
            return new ObjectStorageException("Database storage '" + storageName + "' failed: " + cause.getMessage() + hint, cause);
        }
    }
}
