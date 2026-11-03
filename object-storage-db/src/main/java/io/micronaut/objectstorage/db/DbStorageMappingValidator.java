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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.db.internal.DbMappingValidation;
import io.micronaut.objectstorage.db.internal.DbStorageRuntime;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Checks that the tables of a database storage exist with the mapped columns, using the JDBC database metadata.
 * Nothing is checked at startup; use this validator in a test, or enable {@code validate-mapping} to check on first
 * use, or expose the result through the health endpoint.
 *
 * <pre>{@code
 * @Inject @Named("documents") DbStorageMappingValidator validator;
 *
 * @Test
 * void mappingIsValid() {
 *     validator.assertValid();
 * }
 * }</pre>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachBean(DbStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = DbStorageConfiguration.class)
public class DbStorageMappingValidator {

    private final DbStorageRuntime runtime;

    DbStorageMappingValidator(@Parameter DbStorageRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * @return The name of the storage.
     */
    @NonNull
    public String getStorageName() {
        return runtime.configuration().getName();
    }

    /**
     * Validates the mapping now.
     *
     * @return The problems found, empty if the mapping is valid.
     */
    @NonNull
    public List<String> validate() {
        DbTableMapping mapping = runtime.context().mapping();
        return runtime.transactions().read(connection -> DbMappingValidation.validate(connection, mapping, runtime.store()));
    }

    /**
     * Validates the mapping now.
     *
     * @throws ObjectStorageException If the mapping is not valid.
     */
    public void assertValid() {
        List<String> problems = validate();
        if (!problems.isEmpty()) {
            throw new ObjectStorageException(DbMappingValidation.message(runtime.configuration(), problems));
        }
    }
}
