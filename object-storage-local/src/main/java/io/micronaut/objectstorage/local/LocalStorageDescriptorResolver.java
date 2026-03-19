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
package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver;

@EachBean(LocalStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = LocalStorageConfiguration.class)
public final class LocalStorageDescriptorResolver implements StorageDescriptorResolver {

    private static final String PROVIDER_ID = "local";

    private final StorageDescriptor descriptor;

    public LocalStorageDescriptorResolver(@Parameter LocalStorageConfiguration configuration) {
        String path = configuration.getPath().toAbsolutePath().normalize().toString();
        this.descriptor = new StorageDescriptor(
            configuration.getName(),
            PROVIDER_ID,
            configuration.getName(),
            path,
            configuration.getName()
        );
    }

    @Override
    public StorageDescriptor resolve() {
        return descriptor;
    }
}
