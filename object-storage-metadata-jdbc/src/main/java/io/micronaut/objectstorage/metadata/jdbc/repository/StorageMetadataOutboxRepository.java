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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository contract for durable outbox entries used by reconciliation.
 */
@Internal
public interface StorageMetadataOutboxRepository {

    void enqueue(@NonNull StorageMetadataOutboxRecord outboxRecord);

    boolean enqueueIfAbsent(@NonNull StorageMetadataOutboxRecord outboxRecord);

    @NonNull
    List<StorageMetadataOutboxRecord> findDueEntries(@NonNull Instant asOf, int limit);

    boolean markProcessing(@NonNull String id,
                           @NonNull Instant asOf,
                           @NonNull Instant updatedAt);

    void markSucceeded(@NonNull String id,
                       @NonNull Instant updatedAt);

    void markRetryOrDeadLetter(@NonNull String id,
                               int attemptCount,
                               @NonNull Instant nextAttemptAt,
                               @NonNull StorageMetadataOutboxStatus status,
                               @NonNull String lastErrorSummary,
                               @NonNull Instant updatedAt);

    @NonNull
    Optional<StorageMetadataOutboxRecord> findById(@NonNull String id);

    @NonNull
    Optional<StorageMetadataOutboxRecord> findByIdempotencyKey(@NonNull String idempotencyKey);
}
