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
package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.internal.DefaultReactiveObjectStorageOperations;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

/**
 * Reactive Oracle Cloud object storage operations.
 *
 * @author Cliponaut
 * @since 3.1.0
 */
@EachBean(OracleCloudStorageConfiguration.class)
@Requires(beans = OracleCloudStorageConfiguration.class)
@Requires(beans = OracleCloudStorageOperations.class)
public class OracleCloudStorageReactiveOperations extends DefaultReactiveObjectStorageOperations<
    PutObjectRequest.Builder,
    PutObjectResponse,
    DeleteObjectResponse> {

    public OracleCloudStorageReactiveOperations(@Parameter OracleCloudStorageConfiguration configuration,
                                                OracleCloudStorageOperations operations,
                                                @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        super(operations, blockingExecutor);
    }
}
