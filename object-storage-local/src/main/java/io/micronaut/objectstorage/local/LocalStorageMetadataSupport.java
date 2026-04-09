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
package io.micronaut.objectstorage.local;

import io.micronaut.objectstorage.metadata.BucketMetadataEntry;
import io.micronaut.objectstorage.metadata.BucketMetadataWrite;
import io.micronaut.objectstorage.metadata.ObjectMetadataEntry;
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class LocalStorageMetadataSupport {

    private static final String METADATA_PREFIX = "metadata.";
    private static final String ATTRIBUTE_PREFIX = "attribute.";
    private static final String SYSTEM_PREFIX = "system.";
    private static final String CONTENT_TYPE_KEY = SYSTEM_PREFIX + "contentType";
    private static final String CONTENT_LENGTH_KEY = SYSTEM_PREFIX + "contentLength";
    private static final String ETAG_KEY = SYSTEM_PREFIX + "etag";
    private static final String LAST_MODIFIED_KEY = SYSTEM_PREFIX + "lastModified";

    private LocalStorageMetadataSupport() {
    }

    static ObjectMetadataEntry<Path> readObjectMetadata(Path metadataFile, String key) throws IOException {
        Properties properties = loadProperties(metadataFile);
        boolean structured = isStructured(properties);
        return new ObjectMetadataEntry<>(
            key,
            structured ? readPrefixed(properties, METADATA_PREFIX) : readLegacyMetadata(properties),
            structured ? readPrefixed(properties, ATTRIBUTE_PREFIX) : Map.of(),
            structured ? nullIfEmpty(properties.getProperty(CONTENT_TYPE_KEY)) : null,
            structured ? parseLong(properties.getProperty(CONTENT_LENGTH_KEY)) : null,
            structured ? nullIfEmpty(properties.getProperty(ETAG_KEY)) : null,
            structured ? parseInstant(properties.getProperty(LAST_MODIFIED_KEY)) : null,
            metadataFile
        );
    }

    static BucketMetadataEntry<Path> readBucketMetadata(Path metadataFile, String name) throws IOException {
        Properties properties = loadProperties(metadataFile);
        boolean structured = isStructured(properties);
        return new BucketMetadataEntry<>(
            name,
            structured ? readPrefixed(properties, METADATA_PREFIX) : readLegacyMetadata(properties),
            structured ? readPrefixed(properties, ATTRIBUTE_PREFIX) : Map.of(),
            metadataFile
        );
    }

    static Properties toProperties(ObjectMetadataWrite write) {
        Properties properties = new Properties();
        putPrefixed(properties, METADATA_PREFIX, write.metadata());
        putPrefixed(properties, ATTRIBUTE_PREFIX, write.attributes());
        putIfPresent(properties, CONTENT_TYPE_KEY, write.contentType());
        putIfPresent(properties, CONTENT_LENGTH_KEY, write.contentLength());
        putIfPresent(properties, ETAG_KEY, write.etag());
        putIfPresent(properties, LAST_MODIFIED_KEY, write.lastModified());
        return properties;
    }

    static Properties toProperties(BucketMetadataWrite write) {
        Properties properties = new Properties();
        putPrefixed(properties, METADATA_PREFIX, write.metadata());
        putPrefixed(properties, ATTRIBUTE_PREFIX, write.attributes());
        return properties;
    }

    private static Properties loadProperties(Path metadataFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream metadataIn = LocalStorageIoSupport.newInputStreamNoFollow(metadataFile)) {
            properties.load(metadataIn);
        }
        return properties;
    }

    private static boolean isStructured(Properties properties) {
        return properties.stringPropertyNames().stream().anyMatch(name ->
            name.startsWith(METADATA_PREFIX) || name.startsWith(ATTRIBUTE_PREFIX) || name.startsWith(SYSTEM_PREFIX)
        );
    }

    private static Map<String, String> readLegacyMetadata(Properties properties) {
        Map<String, String> metadata = new HashMap<>(properties.size());
        for (String name : properties.stringPropertyNames()) {
            metadata.put(name, properties.getProperty(name));
        }
        return metadata;
    }

    private static Map<String, String> readPrefixed(Properties properties, String prefix) {
        Map<String, String> values = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                values.put(name.substring(prefix.length()), properties.getProperty(name));
            }
        }
        return values;
    }

    private static void putPrefixed(Properties properties, String prefix, Map<String, String> values) {
        values.forEach((key, value) -> properties.setProperty(prefix + key, value));
    }

    private static void putIfPresent(Properties properties, String key, @Nullable Object value) {
        if (value != null) {
            properties.setProperty(key, value.toString());
        }
    }

    @Nullable
    private static Long parseLong(@Nullable String value) {
        return value == null || value.isEmpty() ? null : Long.parseLong(value);
    }

    @Nullable
    private static Instant parseInstant(@Nullable String value) {
        return value == null || value.isEmpty() ? null : Instant.parse(value);
    }

    @Nullable
    private static String nullIfEmpty(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
