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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import java.util.Set;

/**
 * Registers named object storage resource loaders.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Factory
@Internal
public class ObjectStorageNamedResourceLoaderFactory {

    private static final Set<String> RESERVED_PREFIXES = Set.of(
        "classpath",
        "file",
        "string",
        "base64",
        ObjectStorageResourceParser.S3_SCHEME,
        ObjectStorageResourceParser.GS_SCHEME,
        ObjectStorageResourceParser.AZB_SCHEME,
        ObjectStorageResourceParser.OS_SCHEME
    );

    @EachBean(ObjectStorageConfiguration.class)
    ResourceLoader namedResourceLoader(ObjectStorageConfiguration configuration,
                                       BeanContext beanContext) {
        String name = configuration.getName();
        if (RESERVED_PREFIXES.contains(name)) {
            throw new DisabledBeanException("Object storage resource loader prefix [" + name + "] is reserved");
        }
        return new NamedObjectStorageResourceLoader(name, beanContext);
    }
}
