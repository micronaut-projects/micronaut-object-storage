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
package io.micronaut.objectstorage.relational;

import io.micronaut.objectstorage.ObjectStorageEntry;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Relational {@link ObjectStorageEntry} implementation.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
public final class RelationalStorageEntry implements ObjectStorageEntry<RelationalStoredObject> {

    private final JdbcRelationalObjectStore objectStore;
    private final RelationalStoredObject storedObject;

    RelationalStorageEntry(JdbcRelationalObjectStore objectStore, RelationalStoredObject storedObject) {
        this.objectStore = objectStore;
        this.storedObject = storedObject;
    }

    @Override
    @NonNull
    public String getKey() {
        return storedObject.key();
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return objectStore.openInputStream(storedObject.key());
    }

    @Override
    @NonNull
    public RelationalStoredObject getNativeEntry() {
        return storedObject;
    }

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        return storedObject.metadata();
    }

    @Override
    @NonNull
    public Optional<String> getContentType() {
        return storedObject.getContentType();
    }
}
