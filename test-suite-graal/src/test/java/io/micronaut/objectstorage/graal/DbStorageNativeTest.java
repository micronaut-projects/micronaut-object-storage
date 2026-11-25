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
package io.micronaut.objectstorage.graal;

import io.micronaut.context.annotation.Property;
import io.micronaut.objectstorage.db.DbStorageEntry;
import io.micronaut.objectstorage.db.DbStorageOperations;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "datasources.default.url", value = "jdbc:h2:mem:native;DB_CLOSE_DELAY=-1")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.dialect", value = "H2")
@Property(name = "micronaut.object-storage.db.files.schema-generate", value = "CREATE")
@Property(name = "micronaut.object-storage.db.files.chunk-size", value = "8")
@MicronautTest(startApplication = false, transactional = false)
class DbStorageNativeTest {

    @Inject
    @Named("files")
    DbStorageOperations operations;

    @Test
    void supportsCoreObjectStorageOperationsInNativeImage() throws Exception {
        String key = "native/input.txt";
        String copyKey = "native/copy.txt";
        byte[] payload = "native-database-object-storage".getBytes(StandardCharsets.UTF_8);
        Map<String, String> metadata = Map.of("source", "native-test");

        operations.upload(new BytesUploadRequest(payload, "text/plain", key, metadata));

        assertTrue(operations.exists(key));
        assertEquals(Set.of(key), operations.listObjects());

        DbStorageEntry entry = operations.<DbStorageEntry>retrieve(key).orElseThrow();
        assertEquals(key, entry.getKey());
        assertEquals(metadata, entry.getMetadata());
        try (InputStream inputStream = entry.getInputStream()) {
            assertArrayEquals(payload, inputStream.readAllBytes());
        }

        operations.copy(key, copyKey);
        assertEquals(Set.of(key, copyKey), operations.listObjects());

        assertTrue(operations.delete(key));
        assertTrue(operations.delete(copyKey));
        assertFalse(operations.exists(key));
    }
}
