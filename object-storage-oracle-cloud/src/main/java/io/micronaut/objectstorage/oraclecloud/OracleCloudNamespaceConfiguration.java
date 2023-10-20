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
package io.micronaut.objectstorage.oraclecloud;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.Toggleable;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

/**
 * Configuration of the namespaces for {@link OracleCloudBucketOperations}. Only needed if you want
 * to use bucket operations, the configuration for
 * {@link io.micronaut.objectstorage.ObjectStorageOperations} is separate.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@EachProperty(OracleCloudNamespaceConfiguration.PREFIX)
public class OracleCloudNamespaceConfiguration implements Toggleable {

    /**
     * Configuration Prefix.
     */
    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + ".oracle-cloud-bucket-operations";

    private boolean enabled = true;

    @NonNull
    private String compartmentId;

    @NonNull
    private String namespace;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCompartmentId() {
        return compartmentId;
    }

    public void setCompartmentId(String compartmentId) {
        this.compartmentId = compartmentId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
