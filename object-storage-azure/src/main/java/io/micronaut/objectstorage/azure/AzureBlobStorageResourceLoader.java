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
package io.micronaut.objectstorage.azure;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.resource.AbstractObjectStorageResourceLoader;
import io.micronaut.objectstorage.resource.ObjectStorageResourceParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Azure Blob Storage {@link io.micronaut.core.io.ResourceLoader} for
 * {@code azb:<account>://<container>/<key>}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
final class AzureBlobStorageResourceLoader extends AbstractObjectStorageResourceLoader {

    private final List<AzureBlobStorageConfiguration> configurations;
    private final Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName;

    AzureBlobStorageResourceLoader(@NonNull List<AzureBlobStorageConfiguration> configurations,
                                   @NonNull Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName) {
        this(configurations, operationsByName, null);
    }

    private AzureBlobStorageResourceLoader(@NonNull List<AzureBlobStorageConfiguration> configurations,
                                           @NonNull Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName,
                                           @Nullable RelativeBase relativeBase) {
        super(relativeBase);
        this.configurations = configurations;
        this.operationsByName = operationsByName;
    }

    @Override
    protected boolean hasRecognizedPrefix(@NonNull String path) {
        return path.startsWith(ObjectStorageResourceParser.AZB_SCHEME + ':');
    }

    @Override
    protected Optional<ResolvedObjectStorageResource> resolveAbsolute(@NonNull String path) {
        return ObjectStorageResourceParser.parseAzureBlobStorageUri(path)
            .flatMap(uri -> {
                List<AzureBlobStorageConfiguration> matches = configurations.stream()
                    .filter(configuration -> configuration.getContainer().equals(uri.container()))
                    .filter(configuration -> extractAccountName(configuration).filter(uri.account()::equals).isPresent())
                    .filter(configuration -> operationsByName.containsKey(configuration.getName()))
                    .toList();
                if (matches.isEmpty()) {
                    return Optional.empty();
                }
                if (matches.size() > 1) {
                    throw new IllegalStateException(
                        "Multiple configured Azure object storages match account [" + uri.account() + "] and container [" + uri.container() + "]"
                    );
                }
                AzureBlobStorageConfiguration configuration = matches.get(0);
                return Optional.of(new ResolvedObjectStorageResource(
                    uri.key(),
                    ObjectStorageResourceParser.AZB_SCHEME + ':' + uri.account() + "://" + uri.container() + '/' + uri.key(),
                    operationsByName.get(configuration.getName())
                ));
            });
    }

    @Override
    protected AbstractObjectStorageResourceLoader withRelativeBase(@NonNull RelativeBase relativeBase) {
        return new AzureBlobStorageResourceLoader(configurations, operationsByName, relativeBase);
    }

    private Optional<String> extractAccountName(AzureBlobStorageConfiguration configuration) {
        try {
            URI uri = URI.create(configuration.getEndpoint());
            String path = uri.getPath();
            if (path != null) {
                String normalized = path.startsWith("/") ? path.substring(1) : path;
                if (!normalized.isEmpty()) {
                    int separator = normalized.indexOf('/');
                    return Optional.of(separator >= 0 ? normalized.substring(0, separator) : normalized);
                }
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return Optional.empty();
            }
            int separator = host.indexOf('.');
            return Optional.of(separator >= 0 ? host.substring(0, separator) : host);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
