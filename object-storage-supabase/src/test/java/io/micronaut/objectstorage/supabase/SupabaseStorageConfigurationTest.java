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
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "micronaut.object-storage.supabase.pictures.bucket", value = "pictures-bucket")
@Property(name = "micronaut.object-storage.supabase.logos.bucket", value = "logos-bucket")
@MicronautTest(startApplication = false)
class SupabaseStorageConfigurationTest {

    @Inject
    BeanContext beanContext;

    @Test
    void thereIsOneConfigurationCorrectlyQualifiedWithTheName() {
        assertTrue(beanContext.containsBean(ObjectStorageOperations.class, Qualifiers.byName("pictures")));
        assertTrue(beanContext.containsBean(ObjectStorageOperations.class, Qualifiers.byName("logos")));
        assertTrue(beanContext.containsBean(SupabaseStorageConfiguration.class, Qualifiers.byName("pictures")));

        SupabaseStorageConfiguration configuration = beanContext.getBean(SupabaseStorageConfiguration.class, Qualifiers.byName("pictures"));

        assertEquals("pictures", configuration.getName());
        assertEquals("pictures-bucket", configuration.getBucket());
        assertTrue(configuration.isEnabled());
    }

    @Test
    void moduleEnabledFlagDoesNotCreateAnExtraNamedConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.object-storage.supabase.default.bucket", "pictures-bucket",
            "micronaut.object-storage.supabase.enabled", true))) {

            assertTrue(context.containsBean(ObjectStorageOperations.class, Qualifiers.byName("default")));
            assertTrue(context.containsBean(SupabaseStorageConfiguration.class, Qualifiers.byName("default")));
            assertFalse(context.containsBean(ObjectStorageOperations.class, Qualifiers.byName("enabled")));
            assertFalse(context.containsBean(SupabaseStorageConfiguration.class, Qualifiers.byName("enabled")));
        }
    }
}
