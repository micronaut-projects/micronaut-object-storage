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

import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Map;

/**
 * Base class for tests against the Supabase Storage container: the {@code default} object storage points at a
 * bucket owned by the test class, created before and removed after every test.
 */
@EnabledIf("io.micronaut.objectstorage.supabase.SupabaseStorageContainer#isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSupabaseStorageTest implements TestPropertyProvider {

    protected static final String TEXT = "micronaut";
    protected static final String NEW_TEXT = "object-storage";
    protected static final String CONTENT_TYPE = "text/plain";

    protected final String bucketName;

    @Inject
    @Named("default")
    SupabaseStorageOperations supabaseStorage;

    @Inject
    @Named("default")
    SupabaseBucketOperations supabaseBucketOperations;

    protected AbstractSupabaseStorageTest(String prefix) {
        this.bucketName = "micronaut-object-storage-" + prefix + "-" + System.currentTimeMillis();
    }

    @BeforeEach
    void createBucket() {
        if (!supabaseBucketOperations.exists(bucketName)) {
            supabaseBucketOperations.create(bucketName);
        }
    }

    @AfterEach
    void deleteBucket() {
        emptyAndDeleteBucket(supabaseStorage, supabaseBucketOperations, bucketName);
    }

    static void emptyAndDeleteBucket(SupabaseStorageOperations storage, SupabaseBucketOperations bucketOperations, String bucket) {
        if (bucketOperations.exists(bucket)) {
            storage.listObjects().forEach(storage::delete);
            bucketOperations.delete(bucket);
        }
    }

    @Override
    public @NonNull Map<String, String> getProperties() {
        return SupabaseStorageContainer.properties(bucketName);
    }
}
