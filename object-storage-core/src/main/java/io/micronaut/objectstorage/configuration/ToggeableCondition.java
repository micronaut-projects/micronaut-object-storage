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

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import java.util.Optional;

/**
 * Condition to check whether a bean should be enabled based on {@link Toggleable} configuration.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.2
 */
@Internal
public class ToggeableCondition implements Condition {
    private static final String DEFAULT_QUALIFIER = "default";

    @Override
    public boolean matches(ConditionContext context) {
        if (context.getBeanResolutionContext() == null) {
            return true;
        }
        AnnotationMetadataProvider component = context.getComponent();
        Optional<Class<?>> configurationClassOptional = component.getAnnotation(EachBean.class).classValue();
        if (configurationClassOptional.isPresent()) {
            Class<?> configurationClass = configurationClassOptional.get();
            if (Toggleable.class.isAssignableFrom(configurationClass)) {
                return matches(context, (Class<Toggleable>) configurationClass);
            }
        }
        return false;
    }

    /**
     * Check whether a specific condition is met.
     *
     * @param context The condition context
     * @param configurationClass A {@link Toggleable} configuration class.
     * @return True if has been met
     */
    public static boolean matches(@NonNull ConditionContext<?> context, @NonNull Class<? extends Toggleable> configurationClass) {
        if (context.getBeanResolutionContext() == null) {
            return true;
        }
        Qualifier qualifier = getCurrentQualifier(context);
        Optional<Toggleable> toggleableOptional = context.findBean(configurationClass, qualifier)
            .filter(Toggleable.class::isInstance)
            .map(Toggleable.class::cast);
        return toggleableOptional.map(Toggleable::isEnabled).orElse(false);
    }

    @NonNull
    private static Qualifier<?> getCurrentQualifier(@NonNull ConditionContext<?> context) {
        if (context.getBeanResolutionContext() != null) {
            Qualifier<?> qualifier = context.getBeanResolutionContext().getCurrentQualifier();
            if (qualifier == null) {
                qualifier = ((QualifiedBeanType<?>) context.getComponent()).getDeclaredQualifier();
            }
            if (qualifier != null) {
                return qualifier;
            }
        }
        return Qualifiers.byName(DEFAULT_QUALIFIER);
    }
}
