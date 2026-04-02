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
package io.micronaut.objectstorage.resource;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Named storage resource loader for {@code <storage-name>://<key>}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.1.0
 */
@Internal
public final class NamedObjectStorageResourceLoader extends AbstractObjectStorageResourceLoader {

    private final String storageName;
    @Nullable
    private final BeanContext beanContext;
    @Nullable
    private final ObjectStorageOperations<?, ?, ?> operations;

    public NamedObjectStorageResourceLoader(@NonNull String storageName,
                                            @NonNull BeanContext beanContext) {
        this(storageName, beanContext, null, null);
    }

    public NamedObjectStorageResourceLoader(@NonNull String storageName,
                                            @NonNull ObjectStorageOperations<?, ?, ?> operations) {
        this(storageName, null, operations, null);
    }

    private NamedObjectStorageResourceLoader(@NonNull String storageName,
                                             @Nullable BeanContext beanContext,
                                             @Nullable ObjectStorageOperations<?, ?, ?> operations,
                                             @Nullable RelativeBase relativeBase) {
        super(relativeBase);
        this.storageName = storageName;
        this.beanContext = beanContext;
        this.operations = operations;
    }

    @Override
    protected boolean hasRecognizedPrefix(@NonNull String path) {
        return path.startsWith(storageName + ':');
    }

    @Override
    protected Optional<ResolvedObjectStorageResource> resolveAbsolute(@NonNull String path) {
        return ObjectStorageResourceParser.parseNamedStorageUri(path, storageName)
            .flatMap(uri -> findOperations().map(operations -> new ResolvedObjectStorageResource(
                uri.key(),
                storageName + "://" + uri.key(),
                operations
            )));
    }

    @Override
    protected AbstractObjectStorageResourceLoader withRelativeBase(@NonNull RelativeBase relativeBase) {
        return new NamedObjectStorageResourceLoader(storageName, beanContext, operations, relativeBase);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<ObjectStorageOperations<?, ?, ?>> findOperations() {
        if (operations != null) {
            return Optional.of(operations);
        }
        if (beanContext == null) {
            return Optional.empty();
        }
        return (Optional) beanContext.findBean(ObjectStorageOperations.class, Qualifiers.byName(storageName));
    }
}
