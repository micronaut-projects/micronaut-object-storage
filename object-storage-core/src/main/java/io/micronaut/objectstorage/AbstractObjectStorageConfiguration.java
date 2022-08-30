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
package io.micronaut.objectstorage;

import io.micronaut.core.annotation.NonNull;

/**
 * Base class for all the cloud-specific configurations.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public abstract class AbstractObjectStorageConfiguration implements ObjectStorageConfiguration {

    public static final String GENERIC_PREFIX = "micronaut.object-storage";

    private String name;

    protected AbstractObjectStorageConfiguration(@NonNull String name) {
        this.name = name;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set.
     */
    public void setName(@NonNull String name) {
        this.name = name;
    }
}
