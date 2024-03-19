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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

/**
 * Azure endpoint configuration properties. This is only relevant if you want to use
 * {@link io.micronaut.objectstorage.bucket.BucketOperations}.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@EachProperty(AzureBlobStorageEndpointConfiguration.PREFIX)
public class AzureBlobStorageEndpointConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "azure-bucket-operations";

    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    @NonNull
    private String endpoint;

    public AzureBlobStorageEndpointConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * The blob service endpoint, in the format of https://{accountName}.blob.core.windows.net.
     *
     * @return the endpoint.
     */
    @NonNull
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @param endpoint The blob service endpoint to set, in the format of https://{accountName}.blob.core.windows.net.
     */
    public void setEndpoint(@NonNull String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Whether to enable or disable this object storage.
     * @since 2.0.2
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
