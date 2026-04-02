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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.internal.DefaultReactiveObjectStorageOperations;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

/**
 * Reactive local storage operations.
 *
 * @author Cliponaut
 * @since 3.1.0
 */
@EachBean(LocalStorageConfiguration.class)
@Requires(beans = LocalStorageConfiguration.class)
@Requires(beans = LocalStorageOperations.class)
@Primary
public class LocalStorageReactiveOperations extends DefaultReactiveObjectStorageOperations<
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile> {

    public LocalStorageReactiveOperations(@Parameter LocalStorageConfiguration configuration,
                                          LocalStorageOperations operations,
                                          @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        super(operations, blockingExecutor);
    }
}
