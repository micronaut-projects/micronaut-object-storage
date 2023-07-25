/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.configuration.ToggeableCondition;

/**
 * Condition to check whether an  Azure object storage should be enabled.
 *
 * {@link ToggeableCondition} can't be used in this module since
 * {@link AzureBlobStorageOperations} has an <code>@EachBean</code> over
 * {@link com.azure.storage.blob.BlobContainerClient}, and not {@link AzureBlobStorageConfiguration}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.2
 */
@Internal
public class AzureBlobStorageEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return ToggeableCondition.matches(context, AzureBlobStorageConfiguration.class);
    }
}
