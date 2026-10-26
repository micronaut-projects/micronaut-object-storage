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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Bean wiring of the module for several named storages. The enabled flags are covered by the shared TCK in
 * {@code SupabaseStorageModuleConfigurationSpec}.
 */
class SupabaseStorageModuleConfigurationTest {

    @Test
    void createsNamedBlockingAndReactiveBeansForEachSupabaseStorageConfiguration() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            SupabaseStorageConfiguration.PREFIX + ".default.bucket", "default-bucket",
            SupabaseStorageConfiguration.PREFIX + ".other.bucket", "other-bucket",
            "supabase.project-id", "abcdefghijklmnopqrst",
            "supabase.publishable-key", "sb_publishable_fixture"), Environment.TEST)) {

            assertNotNull(ctx.getBean(SupabaseStorageOperations.class, Qualifiers.byName("default")));
            assertNotNull(ctx.getBean(SupabaseStorageOperations.class, Qualifiers.byName("other")));
            assertInstanceOf(SupabaseBucketOperations.class, ctx.getBean(BucketOperations.class, Qualifiers.byName("default")));
            assertInstanceOf(SupabaseStorageReactiveOperations.class, ctx.getBean(ReactiveObjectStorageOperations.class, Qualifiers.byName("default")));
            assertInstanceOf(SupabaseStorageReactiveOperations.class, ctx.getBean(ReactiveObjectStorageOperations.class, Qualifiers.byName("other")));
            assertInstanceOf(SupabaseReactiveBucketOperations.class, ctx.getBean(ReactiveBucketOperations.class, Qualifiers.byName("other")));
        }
    }
}
