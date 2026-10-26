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
package io.micronaut.objectstorage.supabase;

import io.micronaut.supabase.storage.BucketSchema;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supabase-specific behaviour of {@link SupabaseBucketOperations} against the Storage container. The portable
 * contract is covered by the shared TCK in {@code SupabaseBucketOperationsSpec}.
 */
@MicronautTest(startApplication = false)
class SupabaseBucketOperationsTest extends AbstractSupabaseStorageTest {

    SupabaseBucketOperationsTest() {
        super("buckets");
    }

    @Test
    void bucketsAreCreatedPrivateAndDeletingAMissingBucketFails() {
        String name = "micronaut-object-storage-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        supabaseBucketOperations.create(name);
        BucketSchema bucket = supabaseBucketOperations.retrieve(name).orElseThrow().nativeEntry();

        assertEquals(name, bucket.id());
        assertEquals(name, bucket.name());
        assertEquals(Boolean.FALSE, bucket.isPublic());

        SupabaseStorageException duplicate = assertThrows(SupabaseStorageException.class, () -> supabaseBucketOperations.create(name));
        assertTrue(duplicate.getError().isPresent());
        assertTrue(duplicate.getMessage().contains(name), duplicate.getMessage());

        supabaseBucketOperations.delete(name);
        assertFalse(supabaseBucketOperations.exists(name));

        SupabaseStorageException missing = assertThrows(SupabaseStorageException.class, () -> supabaseBucketOperations.delete(name));
        assertTrue(missing.hasCode(SupabaseStorageException.NO_SUCH_BUCKET));
        assertTrue(missing.isNotFound());
    }
}
