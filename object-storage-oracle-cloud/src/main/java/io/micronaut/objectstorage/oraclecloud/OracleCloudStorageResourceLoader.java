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
package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.auth.RegionProvider;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.resource.AbstractObjectStorageResourceLoader;
import io.micronaut.objectstorage.resource.ObjectStorageResourceParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Oracle Cloud {@link io.micronaut.core.io.ResourceLoader} for
 * {@code os:<region>:<namespace>://<bucket>/<key>}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.1.0
 */
@Internal
final class OracleCloudStorageResourceLoader extends AbstractObjectStorageResourceLoader {

    @Nullable
    private final BeanContext beanContext;
    private final List<OracleCloudStorageConfiguration> configurations;
    @Nullable
    private final Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName;
    @Nullable
    private final RegionProvider regionProvider;

    OracleCloudStorageResourceLoader(@NonNull BeanContext beanContext,
                                     @NonNull List<OracleCloudStorageConfiguration> configurations) {
        this(beanContext, configurations, null);
    }

    OracleCloudStorageResourceLoader(@NonNull List<OracleCloudStorageConfiguration> configurations,
                                     @NonNull Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName,
                                     @NonNull RegionProvider regionProvider) {
        this(null, configurations, operationsByName, regionProvider, null);
    }

    private OracleCloudStorageResourceLoader(@Nullable BeanContext beanContext,
                                             @NonNull List<OracleCloudStorageConfiguration> configurations,
                                             @Nullable Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName,
                                             @Nullable RegionProvider regionProvider,
                                             @Nullable RelativeBase relativeBase) {
        super(relativeBase);
        this.beanContext = beanContext;
        this.configurations = configurations;
        this.operationsByName = operationsByName;
        this.regionProvider = regionProvider;
    }

    private OracleCloudStorageResourceLoader(@NonNull BeanContext beanContext,
                                             @NonNull List<OracleCloudStorageConfiguration> configurations,
                                             @Nullable RelativeBase relativeBase) {
        this(beanContext, configurations, null, null, relativeBase);
    }

    private OracleCloudStorageResourceLoader(@NonNull List<OracleCloudStorageConfiguration> configurations,
                                             @Nullable Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName,
                                             @Nullable RegionProvider regionProvider,
                                             @Nullable RelativeBase relativeBase) {
        this(null, configurations, operationsByName, regionProvider, relativeBase);
    }

    @Override
    protected boolean hasRecognizedPrefix(@NonNull String path) {
        return path.startsWith(ObjectStorageResourceParser.OS_SCHEME + ':');
    }

    @Override
    protected Optional<ResolvedObjectStorageResource> resolveAbsolute(@NonNull String path) {
        Optional<RegionProvider> regionProvider = findRegionProvider();
        if (regionProvider.isEmpty()) {
            return Optional.empty();
        }
        return ObjectStorageResourceParser.parseOracleCloudStorageUri(path)
            .flatMap(uri -> {
                if (!regionProvider.orElseThrow().getRegion().getRegionId().equals(uri.region())) {
                    return Optional.empty();
                }
                List<ResolvedObjectStorageResource> matches = configurations.stream()
                    .filter(configuration -> configuration.getBucket().equals(uri.bucket()))
                    .filter(configuration -> configuration.getNamespace().equals(uri.namespace()))
                    .map(configuration -> findOperations(configuration.getName())
                        .map(operations -> new ResolvedObjectStorageResource(
                            uri.key(),
                            ObjectStorageResourceParser.OS_SCHEME + ':' + uri.region() + ':' + uri.namespace() + "://" + uri.bucket() + '/' + uri.key(),
                            operations
                        )))
                    .flatMap(Optional::stream)
                    .toList();
                if (matches.isEmpty()) {
                    return Optional.empty();
                }
                if (matches.size() > 1) {
                    throw new IllegalStateException(
                        "Multiple configured Oracle Cloud object storages match namespace [" + uri.namespace() + "] and bucket [" + uri.bucket() + "]"
                    );
                }
                return Optional.of(matches.get(0));
            });
    }

    @Override
    protected AbstractObjectStorageResourceLoader withRelativeBase(@NonNull RelativeBase relativeBase) {
        return beanContext != null
            ? new OracleCloudStorageResourceLoader(beanContext, configurations, relativeBase)
            : new OracleCloudStorageResourceLoader(configurations, operationsByName, regionProvider, relativeBase);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<ObjectStorageOperations<?, ?, ?>> findOperations(@NonNull String name) {
        if (beanContext != null) {
            return (Optional) beanContext.findBean(ObjectStorageOperations.class, Qualifiers.byName(name));
        }
        return Optional.ofNullable(operationsByName).map(map -> map.get(name));
    }

    private Optional<RegionProvider> findRegionProvider() {
        if (beanContext != null) {
            return beanContext.findBean(RegionProvider.class);
        }
        return Optional.ofNullable(regionProvider);
    }
}
