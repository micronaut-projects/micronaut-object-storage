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
package io.micronaut.objectstorage.oraclecloud;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.AbstractObjectStorageConfiguration;

/**
 * @author Pavol Gressa
 */
@EachProperty("micronaut.object-storage.oracle-cloud")
public class OracleCloudBucketConfiguration extends AbstractObjectStorageConfiguration {

    private String namespace;

    public OracleCloudBucketConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return the namespace
     */
    @NonNull
    public String getNamespace() {
        return namespace;
    }

    /**
     * @param namespace the namespace to set
     */
    public void setNamespace(@NonNull String namespace) {
        this.namespace = namespace;
    }
}
