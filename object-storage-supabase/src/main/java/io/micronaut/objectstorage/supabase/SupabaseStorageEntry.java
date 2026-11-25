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

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.supabase.storage.ObjectInfoSchema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link ObjectStorageEntry} implementation for Supabase Storage.
 *
 * <p>Supabase Storage serves the object content and its metadata through different routes, so the entry holds
 * the downloaded content in memory together with the object information.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
public class SupabaseStorageEntry implements ObjectStorageEntry<ObjectInfoSchema> {

    @NonNull
    private final String key;
    private final byte @NonNull [] content;
    @NonNull
    private final ObjectInfoSchema info;
    @Nullable
    private final String contentType;

    /**
     * @param key The object key
     * @param content The object content
     * @param info The object information as returned by the Supabase Storage API
     * @param contentType The {@code Content-Type} header of the download, used when the object information has none
     */
    public SupabaseStorageEntry(@NonNull String key,
                                byte @NonNull [] content,
                                @NonNull ObjectInfoSchema info,
                                @Nullable String contentType) {
        this.key = key;
        this.content = content;
        this.info = info;
        this.contentType = contentType;
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * @return A new stream over the downloaded content. Every call returns a fresh stream positioned at the start.
     */
    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    @NonNull
    public ObjectInfoSchema getNativeEntry() {
        return info;
    }

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        Map<String, Object> metadata = info.metadata();
        if (CollectionUtils.isEmpty(metadata)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>(metadata.size());
        metadata.forEach((name, value) -> result.put(name, value == null ? null : String.valueOf(value)));
        return Collections.unmodifiableMap(result);
    }

    @Override
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(info.contentType())
            .or(() -> Optional.ofNullable(contentType));
    }
}
