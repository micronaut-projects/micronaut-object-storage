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
package io.micronaut.objectstorage.googlecloud;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import static io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration.PREFIX;

/**
 * Google Cloud object storage configuration properties.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachProperty(PREFIX)
public class GoogleCloudStorageConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "gcp";

    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    @NonNull
    private String bucket;

    public GoogleCloudStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return The name of the Google Cloud Storage bucket.
     */
    @NonNull
    public String getBucket() {
        return bucket;
    }

    /**
     * @param bucket The name of the Google Cloud Storage bucket.
     */
    public void setBucket(@NonNull String bucket) {
        this.bucket = bucket;
    }

}
