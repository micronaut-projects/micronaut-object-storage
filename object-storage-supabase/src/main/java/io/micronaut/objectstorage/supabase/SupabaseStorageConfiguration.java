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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.EachPropertyContainsEntriesCondition;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;
import org.jspecify.annotations.NonNull;

import static io.micronaut.objectstorage.supabase.SupabaseStorageConfiguration.PREFIX;

/**
 * Supabase Storage object storage configuration properties.
 *
 * <p>The Supabase project itself (URL, API keys) is configured through the {@code supabase.*} properties of
 * Micronaut Supabase; this configuration only selects the bucket each named object storage works with.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
@EachProperty(PREFIX)
@Requires(condition = EachPropertyContainsEntriesCondition.class)
@Introspected
public class SupabaseStorageConfiguration extends AbstractObjectStorageConfiguration {

    /**
     * Configuration Prefix ending.
     */
    public static final String NAME = "supabase";

    /**
     * Configuration Prefix.
     */
    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    /**
     * Bucket name.
     */
    @NonNull
    private String bucket;

    /**
     * @param name Bean Qualifier name
     */
    public SupabaseStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * The name of the Supabase Storage bucket.
     *
     * @return The name of the Supabase Storage bucket.
     */
    @NonNull
    public String getBucket() {
        return bucket;
    }

    /**
     * @param bucket The name of the Supabase Storage bucket.
     */
    public void setBucket(@NonNull String bucket) {
        this.bucket = bucket;
    }

    /**
     * Whether to enable or disable this object storage.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
