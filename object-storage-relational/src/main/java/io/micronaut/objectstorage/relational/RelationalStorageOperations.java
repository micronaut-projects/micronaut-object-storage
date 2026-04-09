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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Relational implementation of {@link ObjectStorageOperations}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@EachBean(RelationalStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = RelationalStorageConfiguration.class)
public class RelationalStorageOperations implements ObjectStorageOperations<
    RelationalStoredObject,
    RelationalStoredObject,
    RelationalStoredObject> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;

    private final JdbcRelationalObjectStore objectStore;

    public RelationalStorageOperations(@Parameter RelationalStorageConfiguration configuration,
                                       BeanContext beanContext) {
        DataSource dataSource = beanContext.getBean(DataSource.class, Qualifiers.byName(configuration.getDatasource()));
        this.objectStore = new JdbcRelationalObjectStore(dataSource, configuration);
    }

    @Override
    @NonNull
    public UploadResponse<RelationalStoredObject> upload(@NonNull UploadRequest request) {
        RelationalStoredObject storedObject = objectStore.upload(request);
        return UploadResponse.of(request.getKey(), storedObject.eTag(), storedObject);
    }

    @Override
    @NonNull
    public UploadResponse<RelationalStoredObject> upload(@NonNull UploadRequest request,
                                                         @NonNull Consumer<RelationalStoredObject> requestConsumer) {
        RelationalStoredObject storedObject = objectStore.upload(request);
        requestConsumer.accept(storedObject);
        return UploadResponse.of(request.getKey(), storedObject.eTag(), storedObject);
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<RelationalStorageEntry> retrieve(@NonNull String key) {
        return objectStore.find(key).map(storedObject -> new RelationalStorageEntry(objectStore, storedObject));
    }

    @Override
    @NonNull
    public RelationalStoredObject delete(@NonNull String key) {
        return objectStore.delete(key);
    }

    @Override
    public boolean exists(@NonNull String key) {
        return objectStore.exists(key);
    }

    @Override
    @NonNull
    public Set<String> listObjects() {
        Set<String> keys = new LinkedHashSet<>();
        String continuationToken = null;
        do {
            ListObjectsResponse response = objectStore.list(new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken));
            keys.addAll(response.getKeys());
            continuationToken = response.getContinuationToken().orElse(null);
        } while (continuationToken != null);
        return keys;
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        return objectStore.list(request);
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        objectStore.copy(sourceKey, destinationKey);
    }
}
