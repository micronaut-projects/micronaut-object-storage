/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.metadata.jdbc;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration properties for JDBC-backed object metadata persistence.
 */
@ConfigurationProperties(ObjectStorageMetadataJdbcConfiguration.PREFIX)
public final class ObjectStorageMetadataJdbcConfiguration implements Toggleable {

    public static final String PREFIX = "micronaut.object-storage.metadata.jdbc";

    private boolean enabled = true;
    @NonNull
    private String datasource = "default";
    @NonNull
    private Reconciliation reconciliation = new Reconciliation();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NonNull
    public String getDatasource() {
        return datasource;
    }

    public void setDatasource(@NonNull String datasource) {
        this.datasource = Objects.requireNonNull(datasource, "datasource");
    }

    @NonNull
    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public void setReconciliation(@NonNull Reconciliation reconciliation) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
    }

    /**
     * Reconciliation worker settings.
     */
    @ConfigurationProperties("reconciliation")
    public static final class Reconciliation {

        @NonNull
        @Bindable(defaultValue = "250ms")
        private Duration fixedDelay = Duration.ofMillis(250);
        @Bindable(defaultValue = "5")
        private int maxAttempts = 5;

        @NonNull
        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(@NonNull Duration fixedDelay) {
            this.fixedDelay = Objects.requireNonNull(fixedDelay, "fixedDelay");
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
