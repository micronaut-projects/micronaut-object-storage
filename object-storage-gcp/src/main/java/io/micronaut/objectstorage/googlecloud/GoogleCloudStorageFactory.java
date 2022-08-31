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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.gcp.GoogleCloudConfiguration;
import io.micronaut.gcp.condition.RequiresGoogleProjectId;
import jakarta.inject.Singleton;

/**
 * @author Pavol Gressa
 * @since 1.0
 */
@Factory
public class GoogleCloudStorageFactory {

    /**
     * @param configuration The Google Cloud Configuration
     * @param googleCredentials        The Google Credentials
     * @return The storage instance
     */
    @RequiresGoogleProjectId
    @Singleton
    @NonNull
    public StorageOptions.Builder builder(@NonNull GoogleCloudConfiguration configuration,
                                          @NonNull GoogleCredentials googleCredentials) {
        return StorageOptions.newBuilder()
            .setProjectId(configuration.getProjectId())
            .setCredentials(googleCredentials);
    }

    /**
     * @param builder the builder
     * @return the {@link Storage}
     */
    @Requires(bean = StorageOptions.Builder.class)
    @Singleton
    @NonNull
    public StorageOptions storageOptions(@NonNull StorageOptions.Builder builder) {
        return builder.build();
    }

    /**
     * @param storageOptions the storage options
     * @return the {@link Storage}
     */
    @Requires(bean = StorageOptions.class)
    @Singleton
    @NonNull
    public Storage storage(@NonNull StorageOptions storageOptions) {
        return storageOptions.getService();
    }
}
