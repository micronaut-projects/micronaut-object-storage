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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageOperations;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Registers the {@code s3:} resource loader.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Factory
@Internal
public class AwsS3ResourceLoaderFactory {

    @Singleton
    @Requires(beans = AwsS3Configuration.class)
    ResourceLoader awsS3ResourceLoader(BeanContext beanContext) {
        List<AwsS3Configuration> configurations = beanContext.getBeansOfType(AwsS3Configuration.class).stream()
            .toList();
        Map<String, ObjectStorageOperations<?, ?, ?>> operationsByName = configurations.stream()
            .map(configuration -> Map.entry(configuration.getName(), findOperations(beanContext, configuration.getName())))
            .filter(entry -> entry.getValue().isPresent())
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().orElseThrow()));
        return new AwsS3ResourceLoader(configurations, operationsByName);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<ObjectStorageOperations<?, ?, ?>> findOperations(BeanContext beanContext, String name) {
        return (Optional) beanContext.findBean(ObjectStorageOperations.class, Qualifiers.byName(name));
    }
}
