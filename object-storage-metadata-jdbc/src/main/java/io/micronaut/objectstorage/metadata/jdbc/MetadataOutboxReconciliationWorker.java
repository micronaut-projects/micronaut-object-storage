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
package io.micronaut.objectstorage.metadata.jdbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook;
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext;
import io.micronaut.objectstorage.metadata.ObjectStorageOperationOutcome;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRecord;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository;
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxStatus;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Singleton
@Internal
@Requires(property = ObjectStorageMetadataJdbcConfiguration.PREFIX + ".enabled", notEquals = "false", defaultValue = "true")
@Requires(bean = StorageMetadataOutboxRepository.class)
@Requires(bean = MetadataOutboxReconciler.class)
final class MetadataOutboxReconciliationWorker {

    private static final int POLL_BATCH_SIZE = 32;

    private final StorageMetadataOutboxRepository outboxRepository;
    private final MetadataOutboxReconciler reconciler;
    private final OutboxStorageDescriptorResolver descriptorResolver;
    private final List<ObjectStorageLifecycleHook> lifecycleHooks;
    private final int maxAttempts;

    MetadataOutboxReconciliationWorker(StorageMetadataOutboxRepository outboxRepository,
                                       MetadataOutboxReconciler reconciler,
                                       OutboxStorageDescriptorResolver descriptorResolver,
                                       List<ObjectStorageLifecycleHook> lifecycleHooks,
                                       ObjectStorageMetadataJdbcConfiguration configuration) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.descriptorResolver = Objects.requireNonNull(descriptorResolver, "descriptorResolver");
        this.lifecycleHooks = ObjectStorageLifecycleHook.ordered(Objects.requireNonNull(lifecycleHooks, "lifecycleHooks"));
        this.maxAttempts = Math.max(1, configuration.getReconciliation().getMaxAttempts());
    }

    @Scheduled(fixedDelay = "${" + ObjectStorageMetadataJdbcConfiguration.PREFIX + ".reconciliation.fixed-delay:250ms}")
    void poll() {
        reconcileDueEntries();
    }

    void reconcileDueEntries() {
        Instant now = Instant.now();
        List<StorageMetadataOutboxRecord> dueEntries = outboxRepository.findDueEntries(now, POLL_BATCH_SIZE);
        for (StorageMetadataOutboxRecord entry : dueEntries) {
            reconcileOne(entry, now);
        }
    }

    private void reconcileOne(StorageMetadataOutboxRecord entry,
                              Instant asOf) {
        Instant now = Instant.now();
        if (!outboxRepository.markProcessing(entry.getId(), asOf, now)) {
            return;
        }
        try {
            reconciler.reconcile(entry);
            Instant succeededAt = Instant.now();
            outboxRepository.markSucceeded(entry.getId(), succeededAt);
            emitAfterHooks(entry);
        } catch (RuntimeException e) {
            handleFailure(entry, e);
        }
    }

    private void emitAfterHooks(StorageMetadataOutboxRecord entry) {
        StorageDescriptor descriptor = descriptorResolver.resolveOrFallback(entry.getStorageName(), entry.getLogicalContainer());
        ObjectStorageOperationContext context = new ObjectStorageOperationContext(
            entry.getTenantId(),
            descriptor,
            entry.getOperationType(),
            entry.getObjectKey().orElse("<unknown>"),
            entry.getDestinationObjectKey().orElse(null),
            null
        );
        ObjectStorageOperationOutcome outcome = switch (entry.getOperationType()) {
            case UPLOAD -> new ObjectStorageOperationOutcome(
                entry.getOperationType(),
                entry.getTenantId(),
                descriptor,
                context.getObjectKey(),
                context.getDestinationObjectKey().orElse(null),
                entry.getReconciliationId().orElse(null),
                null,
                null
            );
            case COPY -> ObjectStorageOperationOutcome.copySuccess(
                context,
                null,
                entry.getReconciliationId().orElse(null)
            );
            case DELETE -> ObjectStorageOperationOutcome.deleteSuccess(
                context,
                null,
                entry.getReconciliationId().orElse(null)
            );
        };
        for (ObjectStorageLifecycleHook lifecycleHook : lifecycleHooks) {
            try {
                lifecycleHook.after(context, outcome);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void handleFailure(StorageMetadataOutboxRecord entry,
                               RuntimeException failure) {
        int attemptCount = entry.getAttemptCount() + 1;
        StorageMetadataOutboxStatus nextStatus = attemptCount >= maxAttempts
            ? StorageMetadataOutboxStatus.DEAD_LETTER
            : StorageMetadataOutboxStatus.FAILED;
        Instant nextAttemptAt = Instant.now().plus(MetadataOutboxRetryPolicy.delayForAttempt(attemptCount));
        String errorSummary = summarize(failure);
        outboxRepository.markRetryOrDeadLetter(
            entry.getId(),
            attemptCount,
            nextAttemptAt,
            nextStatus,
            errorSummary,
            Instant.now()
        );
        emitErrorHook(entry, failure, attemptCount, nextStatus);
    }

    private void emitErrorHook(StorageMetadataOutboxRecord entry,
                               RuntimeException failure,
                               int attemptCount,
                               StorageMetadataOutboxStatus status) {
        StorageDescriptor descriptor = descriptorResolver.resolveOrFallback(entry.getStorageName(), entry.getLogicalContainer());
        String objectKey = entry.getObjectKey().orElse("<unknown>");
        ObjectStorageOperationContext context = new ObjectStorageOperationContext(
            entry.getTenantId(),
            descriptor,
            entry.getOperationType(),
            objectKey,
            null,
            null
        );
        ObjectStorageException exception = new ObjectStorageException(
            "Outbox reconciliation failed for record '"
                + entry.getId()
                + "' (attempt="
                + attemptCount
                + ", status="
                + status
                + ")"
                + entry.getReconciliationId().map(it -> " reconciliationId=" + it).orElse(""),
            failure
        );
        for (ObjectStorageLifecycleHook lifecycleHook : lifecycleHooks) {
            try {
                lifecycleHook.error(context, exception);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @NonNull
    private static String summarize(Throwable throwable) {
        String message = throwable.getMessage();
        String value = (message == null || message.isBlank())
            ? throwable.getClass().getSimpleName()
            : throwable.getClass().getSimpleName() + ": " + message;
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
