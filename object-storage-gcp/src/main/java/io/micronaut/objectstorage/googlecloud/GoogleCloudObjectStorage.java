/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.objectstorage.googlecloud;


import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.objectstorage.*;

import java.util.Optional;

/**
 * @author Pavol Gressa
 */
@EachBean(GoogleCloudObjectStorageConfiguration.class)
public class GoogleCloudObjectStorage implements ObjectStorage {

    private final InputStreamMapper inputStreamMapper;
    private final Storage storage;
    private final GoogleCloudObjectStorageConfiguration googleCloudObjectStorageConfiguration;

    public GoogleCloudObjectStorage(InputStreamMapper inputStreamMapper, Storage storage, GoogleCloudObjectStorageConfiguration googleCloudObjectStorageConfiguration) {
        this.inputStreamMapper = inputStreamMapper;
        this.storage = storage;
        this.googleCloudObjectStorageConfiguration = googleCloudObjectStorageConfiguration;
    }

    @Override
    public UploadResponse put(UploadRequest uploadRequest) throws ObjectStorageException {
        BlobId blobId = BlobId.of(googleCloudObjectStorageConfiguration.getName(), uploadRequest.getKey());
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType(uploadRequest.getContentType().orElse(null))
            .build();

        Blob blob = storage.create(blobInfo, inputStreamMapper.toByteArray(uploadRequest.getInputStream()));
        return new UploadResponse.Builder().withETag(blob.getEtag()).build();
    }

    @Override
    public Optional<ObjectStorageEntry> get(String key) throws ObjectStorageException {
        BlobId blobId = BlobId.of(googleCloudObjectStorageConfiguration.getName(), key);
        Blob blob = storage.get(blobId);

        GoogleCloudObjectStorageEntry storageEntry = null;
        if (blob != null && blob.exists()) {
            storageEntry = new GoogleCloudObjectStorageEntry(blob);
        }
        return Optional.ofNullable(storageEntry);
    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        BlobId blobId = BlobId.of(googleCloudObjectStorageConfiguration.getName(), key);
        storage.delete(blobId);
    }
}
