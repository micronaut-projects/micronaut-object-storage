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
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Optional;

/**
 * Condition to check whether an object storage should be enabled.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.2
 */
@Internal
public class ObjectStorageEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        if (context.getBeanResolutionContext() == null) {
            return true;
        } else {
            AnnotationMetadataProvider component = context.getComponent();
            Optional<Class<?>> configurationClass = component.getAnnotation(EachBean.class).classValue();
            if (configurationClass.isPresent()) {
                Qualifier qualifier = getCurrentQualifier(context);
                if (context.getBeanContext().containsBean(configurationClass.get(), qualifier)) {
                    ObjectStorageConfiguration configuration = (ObjectStorageConfiguration) context.getBean(configurationClass.get(), qualifier);
                    return configuration.isEnabled();
                }
            }
        }
        return false;
    }

    public static Qualifier<?> getCurrentQualifier(ConditionContext<?> context) {
        if (context.getBeanResolutionContext() != null) {
            Qualifier<?> qualifier = context.getBeanResolutionContext().getCurrentQualifier();
            if (qualifier == null) {
                qualifier = ((QualifiedBeanType<?>) context.getComponent()).getDeclaredQualifier();
            }
            if (qualifier != null) {
                return qualifier;
            }
        }
        return Qualifiers.byName("default");
    }
}
