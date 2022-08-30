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
import io.micronaut.objectstorage.AbstractObjectStorageConfiguration;

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX;

/**
 * Azure object storage configuration properties.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachProperty(PREFIX)
public class AzureBlobStorageConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "azure";

    public static final String PREFIX = GENERIC_PREFIX + '.' + NAME;

    private String endpoint;

    public AzureBlobStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return the endpoint.
     */
    @NonNull
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @param endpoint The blob service endpoint to set, in the format of
     *                 <code>https://{accountName}.blob.core.windows.net</code> foo
     */
    public void setEndpoint(@NonNull String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * The blob container name.
     */
    @Override
    public void setName(String name) {
        super.setName(name);
    }
}
