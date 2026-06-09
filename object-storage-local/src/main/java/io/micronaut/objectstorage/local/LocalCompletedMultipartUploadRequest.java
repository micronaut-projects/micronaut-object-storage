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

import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.FileUploadRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

final class LocalCompletedMultipartUploadRequest extends FileUploadRequest {

    @NonNull
    private final Path path;

    LocalCompletedMultipartUploadRequest(@NonNull String keyName,
                                         @Nullable String contentType,
                                         @NonNull Path path,
                                         @NonNull Map<String, String> metadata) {
        super(keyName, contentType, path, metadata);
        this.path = path;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        try {
            return LocalStorageIoSupport.newInputStreamNoFollow(path);
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading completed multipart file: " + path, e);
        }
    }
}
