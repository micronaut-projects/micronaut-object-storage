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
package io.micronaut.objectstorage.db.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.AbstractHealthIndicator;
import io.micronaut.objectstorage.db.DbStorageConfiguration;
import io.micronaut.objectstorage.db.DbStorageMappingValidator;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports whether the tables of every database storage exist with the mapped columns. The check reads the JDBC
 * database metadata each time the health endpoint is called, never at startup.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Singleton
@Requires(classes = HealthEndpoint.class)
@Requires(beans = HealthEndpoint.class)
@Requires(property = DbStorageHealthIndicator.ENABLED, notEquals = StringUtils.FALSE)
public class DbStorageHealthIndicator extends AbstractHealthIndicator<Map<String, Object>> {

    /**
     * Property to disable the indicator.
     */
    public static final String ENABLED = DbStorageConfiguration.PREFIX + ".health.enabled";

    private static final String NAME = "objectStorageDb";

    private final List<DbStorageMappingValidator> validators;

    /**
     * @param validators The validators of every database storage.
     */
    public DbStorageHealthIndicator(List<DbStorageMappingValidator> validators) {
        this.validators = validators;
    }

    @Override
    protected Map<String, Object> getHealthInformation() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean up = true;
        for (DbStorageMappingValidator validator : validators) {
            Object result;
            try {
                List<String> problems = validator.validate();
                up &= problems.isEmpty();
                result = problems.isEmpty() ? "UP" : problems;
            } catch (RuntimeException e) {
                up = false;
                result = e.getMessage();
            }
            details.put(validator.getStorageName(), result);
        }
        healthStatus = up ? HealthStatus.UP : HealthStatus.DOWN;
        return details;
    }

    @Override
    protected String getName() {
        return NAME;
    }
}
