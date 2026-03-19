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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver;

@EachBean(AwsS3Configuration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = AwsS3Configuration.class)
public final class AwsS3StorageDescriptorResolver implements StorageDescriptorResolver {

    private static final String PROVIDER_ID = "aws";

    private final StorageDescriptor descriptor;

    public AwsS3StorageDescriptorResolver(@Parameter AwsS3Configuration configuration) {
        this.descriptor = new StorageDescriptor(
            configuration.getName(),
            PROVIDER_ID,
            configuration.getBucket(),
            null,
            configuration.getBucket()
        );
    }

    @Override
    public StorageDescriptor resolve() {
        return descriptor;
    }
}
