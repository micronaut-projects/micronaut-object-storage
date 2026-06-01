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
package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.internal.DefaultReactiveObjectMetadataOperations;
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * Reactive local object metadata operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(LocalStorageConfiguration.class)
@Requires(beans = LocalStorageConfiguration.class)
@Requires(beans = ObjectMetadataOperations.class)
public class LocalStorageReactiveObjectMetadataOperations extends DefaultReactiveObjectMetadataOperations<Path> {

    /**
     * Create reactive local object metadata operations.
     *
     * @param operations The local object metadata operations.
     * @param blockingExecutor The blocking executor.
     * @deprecated Use {@link #LocalStorageReactiveObjectMetadataOperations(ObjectMetadataOperations, ExecutorService)} instead.
     */
    @Deprecated(since = "3.0.0")
    public LocalStorageReactiveObjectMetadataOperations(LocalStorageObjectMetadataOperations operations,
                                                        @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        this((ObjectMetadataOperations<Path>) operations, blockingExecutor);
    }

    @Inject
    public LocalStorageReactiveObjectMetadataOperations(ObjectMetadataOperations<Path> operations,
                                                        @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        super(operations, blockingExecutor);
    }
}
