/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.objectstorage.configuration;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Rejects malformed {@link EachProperty} beans produced from scalar sibling properties.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.2
 */
@Internal
public class EachPropertyContainsEntriesCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        if (context.getBeanResolutionContext() == null) {
            return true;
        }
        Optional<String> qualifierName = getCurrentQualifierName(context);
        Optional<String> prefix = context.getComponent()
            .findAnnotation(EachProperty.class)
            .flatMap(annotationValue -> annotationValue.stringValue());
        if (qualifierName.isEmpty() || prefix.isEmpty()) {
            return true;
        }
        String propertyPrefix = prefix.get() + '.' + qualifierName.get();
        return context.containsProperties(propertyPrefix) && !context.containsProperty(propertyPrefix);
    }

    @NonNull
    private static Optional<String> getCurrentQualifierName(@NonNull ConditionContext<?> context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        if (beanResolutionContext == null) {
            return Optional.empty();
        }
        Qualifier<?> qualifier = beanResolutionContext.getCurrentQualifier();
        if (qualifier == null && context.getComponent() instanceof QualifiedBeanType<?> qualifiedBeanType) {
            qualifier = qualifiedBeanType.getDeclaredQualifier();
        }
        return Optional.ofNullable(qualifier)
            .map(Qualifiers::findName);
    }
}
