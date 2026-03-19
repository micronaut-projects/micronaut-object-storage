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
package io.micronaut.objectstorage.azure;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver;

@EachBean(AzureBlobStorageConfiguration.class)
public final class AzureBlobStorageDescriptorResolver implements StorageDescriptorResolver {

    private static final String PROVIDER_ID = "azure";

    private final StorageDescriptor descriptor;

    public AzureBlobStorageDescriptorResolver(@Parameter AzureBlobStorageConfiguration configuration) {
        this.descriptor = new StorageDescriptor(
            configuration.getName(),
            PROVIDER_ID,
            configuration.getContainer(),
            null,
            configuration.getContainer()
        );
    }

    @Override
    public StorageDescriptor resolve() {
        return descriptor;
    }
}
