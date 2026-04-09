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
package io.micronaut.objectstorage.relational;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.EachPropertyContainsEntriesCondition;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Relational object storage configuration.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachProperty(RelationalStorageConfiguration.PREFIX)
@Requires(condition = EachPropertyContainsEntriesCondition.class)
@Introspected
public class RelationalStorageConfiguration extends AbstractObjectStorageConfiguration {

    /**
     * Configuration prefix ending.
     */
    public static final String NAME = "relational";

    /**
     * Configuration prefix.
     */
    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    @Nullable
    private String datasource;

    @Nullable
    private String schema;

    @NonNull
    private String tableName = "micronaut_object_storage_object";

    @Nullable
    private String keyPrefix;

    public RelationalStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return The datasource bean name to use. Defaults to the storage bean name.
     */
    @NonNull
    public String getDatasource() {
        return datasource == null || datasource.isBlank() ? getName() : datasource;
    }

    /**
     * @param datasource The datasource bean name to use.
     */
    public void setDatasource(@Nullable String datasource) {
        this.datasource = normalizeOptionalValue(datasource);
    }

    /**
     * @return The optional schema to create and use.
     */
    @NonNull
    public Optional<String> getSchema() {
        return Optional.ofNullable(schema);
    }

    /**
     * @param schema The optional schema to create and use.
     */
    public void setSchema(@Nullable String schema) {
        this.schema = normalizeIdentifier(schema, "schema");
    }

    /**
     * @return The table name used for object rows.
     */
    @NonNull
    public String getTableName() {
        return tableName;
    }

    /**
     * @param tableName The table name used for object rows.
     */
    public void setTableName(@NonNull String tableName) {
        String normalized = normalizeIdentifier(tableName, "tableName");
        if (normalized == null) {
            throw new ConfigurationException("The relational tableName must not be blank");
        }
        this.tableName = normalized;
    }

    /**
     * @return The optional internal key prefix used to namespace stored rows.
     */
    @NonNull
    public Optional<String> getKeyPrefix() {
        return Optional.ofNullable(keyPrefix);
    }

    /**
     * @param keyPrefix The optional internal key prefix used to namespace stored rows.
     */
    public void setKeyPrefix(@Nullable String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            this.keyPrefix = null;
            return;
        }
        String normalized = keyPrefix;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.keyPrefix = normalized.isEmpty() ? null : normalized + '/';
    }

    /**
     * Whether to enable or disable this object storage.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    private static String normalizeOptionalValue(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Nullable
    private static String normalizeIdentifier(@Nullable String value, @NonNull String propertyName) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            return null;
        }
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new ConfigurationException("The relational " + propertyName + " must match " + IDENTIFIER.pattern());
        }
        return normalized;
    }
}
