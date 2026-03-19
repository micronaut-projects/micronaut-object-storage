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

import io.micronaut.core.order.Ordered;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Ordered lifecycle hook for metadata-aware storage workflows.
 *
 * Hooks run for upload, copy, and delete operations using deterministic
 * {@link Ordered} precedence.
 *
 * @since 1.6.0
 */
public interface ObjectStorageLifecycleHook extends Ordered {

    Comparator<ObjectStorageLifecycleHook> ORDER_COMPARATOR =
        Comparator.<ObjectStorageLifecycleHook>comparingInt(ObjectStorageLifecycleHook::getOrder)
            .thenComparing(hook -> hook.getClass().getName());

    /**
     * Callback before the delegate storage operation is invoked.
     *
     * @param context Operation context.
     */
    default void before(@NonNull ObjectStorageOperationContext context) {
    }

    /**
     * Callback after a successful delegate storage operation.
     *
     * @param context Operation context.
     * @param outcome Operation outcome.
     */
    default void after(@NonNull ObjectStorageOperationContext context,
                       @NonNull ObjectStorageOperationOutcome outcome) {
    }

    /**
     * Callback after a failed delegate storage or metadata dispatch operation.
     *
     * @param context Operation context.
     * @param throwable Failure.
     */
    default void error(@NonNull ObjectStorageOperationContext context,
                       @NonNull Throwable throwable) {
    }

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * @param hooks Hook collection.
     * @return Hooks ordered deterministically by {@link Ordered#getOrder()}.
     */
    @NonNull
    static List<ObjectStorageLifecycleHook> ordered(@NonNull Collection<ObjectStorageLifecycleHook> hooks) {
        return hooks.stream().sorted(ORDER_COMPARATOR).toList();
    }
}
