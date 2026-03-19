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
package io.micronaut.objectstorage.metadata;

import io.micronaut.objectstorage.ObjectStorageException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Raised when delegate storage succeeds but metadata dispatch/enqueue fails.
 *
 * @since 1.6.0
 */
public final class MetadataDispatchException extends ObjectStorageException {

    private final ObjectStorageOperationContext operationContext;
    private final ObjectStorageOperationOutcome operationOutcome;
    @Nullable
    private final String reconciliationId;

    public MetadataDispatchException(@NonNull ObjectStorageOperationContext operationContext,
                                     @NonNull ObjectStorageOperationOutcome operationOutcome,
                                     @Nullable String reconciliationId,
                                     @NonNull Throwable cause) {
        super(buildMessage(operationContext, reconciliationId), Objects.requireNonNull(cause, "cause"));
        this.operationContext = Objects.requireNonNull(operationContext, "operationContext");
        this.operationOutcome = Objects.requireNonNull(operationOutcome, "operationOutcome");
        this.reconciliationId = reconciliationId;
    }

    @NonNull
    public ObjectStorageOperationContext getOperationContext() {
        return operationContext;
    }

    @NonNull
    public ObjectStorageOperationOutcome getOperationOutcome() {
        return operationOutcome;
    }

    @NonNull
    public Optional<String> getReconciliationId() {
        return Optional.ofNullable(reconciliationId);
    }

    @NonNull
    private static String buildMessage(@NonNull ObjectStorageOperationContext context,
                                       @Nullable String reconciliationId) {
        Objects.requireNonNull(context, "context");
        StringBuilder message = new StringBuilder("Metadata dispatch failed after successful ")
            .append(context.getOperationType())
            .append(" for tenant '")
            .append(context.getTenantId())
            .append("' on storage '")
            .append(context.getStorageDescriptor().getStorageName())
            .append("' and key '")
            .append(context.getObjectKey())
            .append("'");
        if (reconciliationId != null && !reconciliationId.isBlank()) {
            message.append(" (reconciliationId=")
                .append(reconciliationId)
                .append(')');
        }
        return message.toString();
    }
}
