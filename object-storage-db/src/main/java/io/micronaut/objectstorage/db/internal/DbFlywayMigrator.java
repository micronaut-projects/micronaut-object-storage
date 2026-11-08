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
package io.micronaut.objectstorage.db.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;

/**
 * Applies the shipped Flyway migrations of a dialect with a dedicated history table, so that they never interfere
 * with the application's own Flyway history. Only loaded when Flyway schema generation is configured.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
final class DbFlywayMigrator {

    private DbFlywayMigrator() {
    }

    /**
     * Migrates the built-in tables.
     *
     * @param dataSource The datasource.
     * @param directory The dialect directory.
     */
    static void migrate(@NonNull DataSource dataSource, @NonNull String directory) {
        // PostgreSQL folds unquoted names to lower case, the other databases to upper case
        String historyTable = "postgres".equals(directory)
            ? DbTableMapping.SCHEMA_HISTORY_TABLE.toLowerCase(java.util.Locale.ROOT)
            : DbTableMapping.SCHEMA_HISTORY_TABLE;
        Flyway.configure(DbFlywayMigrator.class.getClassLoader())
            .dataSource(dataSource)
            .locations("classpath:" + DbSchemaScripts.FLYWAY_LOCATION + directory)
            .table(historyTable)
            // the application's tables may already be there: start our own history at version 0
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate();
    }
}
