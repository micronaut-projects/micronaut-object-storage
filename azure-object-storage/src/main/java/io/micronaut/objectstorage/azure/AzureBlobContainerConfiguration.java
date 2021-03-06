/*
 * Copyright 2017-2021 original authors
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

/**
 * @author Pavol Gressa
 */
@EachProperty(AzureBlobContainerConfiguration.PREFIX)
public class AzureBlobContainerConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "azure-container";
    public static final String PREFIX = AbstractObjectStorageConfiguration.GENERIC_PREFIX + "." + NAME;

    private String endpoint;

    public AzureBlobContainerConfiguration(@Parameter String name) {
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
     * @param endpoint the endpoint to set.
     */
    public void setEndpoint(@NonNull String endpoint) {
        this.endpoint = endpoint;
    }

}
