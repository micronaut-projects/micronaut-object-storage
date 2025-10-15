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
package io.micronaut.objectstorage.request;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.StreamingFileUpload;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Object storage upload request.
 * @since 1.0.0
 */
public interface UploadRequest {

    /**
     * @param path the path to the file.
     * @return An {@link UploadRequest} from the given path.
     */
    @NonNull
    static UploadRequest fromPath(@NonNull Path path) {
        return new FileUploadRequest(path);
    }

    /**
     * @param path the path to the file.
     * @param prefix the prefix under which the file will be stored (<code>prefix/fileName</code>).
     * @return An {@link UploadRequest} from the given path and prefix
     */
    @NonNull
    static UploadRequest fromPath(@NonNull Path path, @NonNull String prefix) {
        return new FileUploadRequest(path, prefix);
    }

    /**
     * @param bytes the source byte array.
     * @param key the key under which the file will be stored (<code>path/to/file</code>)
     * @return An {@link UploadRequest} from the given byte array and key
     */
    @NonNull
    static UploadRequest fromBytes(@NonNull byte[] bytes, @NonNull String key) {
        return new BytesUploadRequest(bytes, key);
    }

    /**
     * @param bytes the source byte array.
     * @param key the key under which the file will be stored (<code>path/to/file</code>)
     * @param contentType the content type of the file to store.
     * @return An {@link UploadRequest} from the given parameters.
     */
    @NonNull
    static UploadRequest fromBytes(@NonNull byte[] bytes,
                                   @NonNull String key,
                                   @NonNull String contentType) {
        return new BytesUploadRequest(bytes, key, contentType);
    }

    /**
     * @param completedFileUpload the {@link CompletedFileUpload}.
     * @return An {@link UploadRequest} from the given {@link CompletedFileUpload}.
     */
    @NonNull
    static UploadRequest fromCompletedFileUpload(@NonNull CompletedFileUpload completedFileUpload) {
        return new CompletedFileUploadRequest(completedFileUpload);
    }

    /**
     * @param completedFileUpload the {@link CompletedFileUpload}.
     * @param key the key under which the file will be stored (<code>path/to/file</code>)
     * @return An {@link UploadRequest} from the given {@link CompletedFileUpload} and key.
     */
    @NonNull
    static UploadRequest fromCompletedFileUpload(@NonNull CompletedFileUpload completedFileUpload,
                                                 @NonNull String key) {
        return new CompletedFileUploadRequest(completedFileUpload, key);
    }
    
    /**
     * @param streamingFileUpload the {@link StreamingFileUpload}.
     * @return An {@link UploadRequest} from the given {@link StreamingFileUpload}.
     * @since 2.7.0
     */
    @NonNull
    static UploadRequest fromStreamingFileUpload(@NonNull StreamingFileUpload streamingFileUpload) {
        return new StreamingFileUploadRequest(streamingFileUpload);
    }
    
    /**
     * @param streamingFileUpload the {@link StreamingFileUpload}.
     * @param key the key under which the file will be stored (<code>path/to/file</code>)
     * @return An {@link UploadRequest} from the given {@link StreamingFileUpload} and key.
     * @since 2.7.0
     */
    @NonNull
    static UploadRequest fromStreamingFileUpload(@NonNull StreamingFileUpload streamingFileUpload,
                                                 @NonNull String key) {
        return new StreamingFileUploadRequest(streamingFileUpload, key);
    }


    /**
     * @return the content type of this upload request.
     */
    @NonNull
    Optional<String> getContentType();

    /**
     * @return the file name with path.
     */
    @NonNull
    String getKey();

    /**
     * @return the size of the file, in bytes.
     */
    @NonNull
    Optional<Long> getContentSize();

    /**
     * @return an input stream of the object to be stored.
     */
    @NonNull
    InputStream getInputStream();

    /**
     * @return a map with key-value pairs to be stored along the file. An empty map by default.
     * @since 1.1.0
     */
    @NonNull
    default Map<String, String> getMetadata() {
        return Collections.emptyMap();
    }

    /**
     * @param metadata a map with key-value pairs to be stored along the file.
     * @since 1.1.0
     */
    default void setMetadata(@NonNull Map<String, String> metadata) {
    }

    /**
     * @param contentType the content type of this upload request.
     * @since 1.1.0
     */
    default void setContentType(@NonNull String contentType) {
    }

}
