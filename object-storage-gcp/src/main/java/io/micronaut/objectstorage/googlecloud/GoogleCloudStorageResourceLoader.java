/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.resource.AbstractObjectStorageResourceLoader;
import io.micronaut.objectstorage.resource.ObjectStorageResourceParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google Cloud Storage {@link io.micronaut.core.io.ResourceLoader} for {@code gs://<bucket>/<key>}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.1.0
 */
@Internal
final class GoogleCloudStorageResourceLoader extends AbstractObjectStorageResourceLoader {

    private final List<GoogleCloudStorageConfiguration> configurations;
    private final Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName;

    GoogleCloudStorageResourceLoader(@NonNull List<GoogleCloudStorageConfiguration> configurations,
                                     @NonNull Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName) {
        this(configurations, operationsByName, null);
    }

    private GoogleCloudStorageResourceLoader(@NonNull List<GoogleCloudStorageConfiguration> configurations,
                                             @NonNull Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName,
                                             @Nullable RelativeBase relativeBase) {
        super(relativeBase);
        this.configurations = configurations;
        this.operationsByName = operationsByName;
    }

    @Override
    protected boolean hasRecognizedPrefix(@NonNull String path) {
        return path.startsWith(ObjectStorageResourceParser.GS_SCHEME + ':');
    }

    @Override
    protected Optional<ResolvedObjectStorageResource> resolveAbsolute(@NonNull String path) {
        return ObjectStorageResourceParser.parseBucketStorageUri(path, ObjectStorageResourceParser.GS_SCHEME)
            .flatMap(uri -> {
                List<GoogleCloudStorageConfiguration> matches = configurations.stream()
                    .filter(configuration -> configuration.getBucket().equals(uri.bucket()))
                    .filter(configuration -> operationsByName.containsKey(configuration.getName()))
                    .toList();
                if (matches.isEmpty()) {
                    return Optional.empty();
                }
                if (matches.size() > 1) {
                    throw new IllegalStateException("Multiple configured Google Cloud object storages match bucket [" + uri.bucket() + "]");
                }
                GoogleCloudStorageConfiguration configuration = matches.get(0);
                return Optional.of(new ResolvedObjectStorageResource(
                    uri.key(),
                    ObjectStorageResourceParser.GS_SCHEME + "://" + uri.bucket() + '/' + uri.key(),
                    operationsByName.get(configuration.getName())
                ));
            });
    }

    @Override
    protected AbstractObjectStorageResourceLoader withRelativeBase(@NonNull RelativeBase relativeBase) {
        return new GoogleCloudStorageResourceLoader(configurations, operationsByName, relativeBase);
    }
}
