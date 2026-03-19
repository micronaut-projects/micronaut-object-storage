/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.metadata;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Queryable persisted object metadata fields.
 */
public enum StorageObjectMetadataField {
    TENANT_ID("tenantId"),
    STORAGE_NAME("storageName"),
    PROVIDER_ID("providerId"),
    LOGICAL_CONTAINER("logicalContainer"),
    PROVIDER_NAMESPACE("providerNamespace"),
    PROVIDER_CONTAINER("providerContainer"),
    PROVIDER_CONTAINER_IDENTITY("providerContainerIdentity"),
    OBJECT_KEY("objectKey"),
    OBJECT_KEY_HASH("objectKeyHash"),
    CONTENT_TYPE("contentType"),
    CONTENT_LENGTH("contentLength"),
    ETAG("etag"),
    PROVIDER_VERSION_ID("providerVersionId"),
    CHECKSUM("checksum"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    RECONCILIATION_STATUS("reconciliationStatus"),
    LAST_ERROR_SUMMARY("lastErrorSummary");

    private final String persistedField;

    StorageObjectMetadataField(String persistedField) {
        this.persistedField = persistedField;
    }

    @NonNull
    public String getPersistedField() {
        return persistedField;
    }

    @NonNull
    public static Set<String> persistedFields() {
        return Arrays.stream(values())
            .map(StorageObjectMetadataField::getPersistedField)
            .collect(Collectors.toUnmodifiableSet());
    }
}
