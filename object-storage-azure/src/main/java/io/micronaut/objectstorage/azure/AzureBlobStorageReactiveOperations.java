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
package io.micronaut.objectstorage.azure;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.internal.DefaultReactiveObjectStorageOperations;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

/**
 * Reactive Azure Blob Storage operations.
 *
 * @author Cliponaut
 * @since 3.1.0
 */
@EachBean(BlobContainerClient.class)
@Requires(beans = BlobContainerClient.class)
@Requires(beans = AzureBlobStorageOperations.class)
@Requires(condition = AzureBlobStorageEnabledCondition.class)
public class AzureBlobStorageReactiveOperations extends DefaultReactiveObjectStorageOperations<
    BlobParallelUploadOptions,
    BlockBlobItem,
    Response<Void>> {

    public AzureBlobStorageReactiveOperations(@Parameter BlobContainerClient blobContainerClient,
                                              AzureBlobStorageOperations operations,
                                              @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        super(operations, blockingExecutor);
    }
}
