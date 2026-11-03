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
package io.micronaut.objectstorage.db;

/**
 * How the built-in tables of a database storage are created.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
public enum DbSchemaGenerate {

    /**
     * The module never runs DDL. Apply the shipped scripts, Liquibase changelog or Flyway migrations yourself.
     */
    NONE,

    /**
     * Run the shipped schema script on first use of the storage if the tables are missing. Handy for development
     * and tests.
     */
    CREATE,

    /**
     * Run the shipped Flyway migrations on first use of the storage, with a dedicated history table
     * ({@code MN_OS_SCHEMA_HISTORY}). Requires Flyway on the classpath.
     */
    FLYWAY
}
