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

import io.micronaut.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable description of a Supabase Storage object upload, the provider-specific request that the
 * {@code upload(UploadRequest, Consumer)} consumer of {@link SupabaseStorageOperations} can customize before the
 * object is sent.
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
public final class SupabaseStorageUploadRequest {

    private String bucketName;
    private String objectKey;
    private String contentType = MediaType.APPLICATION_OCTET_STREAM;
    @Nullable
    private String cacheControl;
    private boolean upsert = true;
    private Map<String, String> metadata = Collections.emptyMap();
    private byte[] body;

    /**
     * @param bucketName The bucket the object is uploaded to
     * @param objectKey The object key
     * @param body The object content
     */
    public SupabaseStorageUploadRequest(@NonNull String bucketName, @NonNull String objectKey, byte @NonNull [] body) {
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.body = Objects.requireNonNull(body, "body");
    }

    /**
     * @return The bucket the object is uploaded to
     */
    @NonNull
    public String getBucketName() {
        return bucketName;
    }

    /**
     * @param bucketName The bucket the object is uploaded to
     */
    public void setBucketName(@NonNull String bucketName) {
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName");
    }

    /**
     * @return The object key
     */
    @NonNull
    public String getObjectKey() {
        return objectKey;
    }

    /**
     * @param objectKey The object key
     */
    public void setObjectKey(@NonNull String objectKey) {
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
    }

    /**
     * @return The content type stored with the object. Defaults to {@code application/octet-stream}.
     */
    @NonNull
    public String getContentType() {
        return contentType;
    }

    /**
     * @param contentType The content type stored with the object
     */
    public void setContentType(@NonNull String contentType) {
        this.contentType = Objects.requireNonNull(contentType, "contentType");
    }

    /**
     * @return The {@code Cache-Control} header stored with the object, if any
     */
    @Nullable
    public String getCacheControl() {
        return cacheControl;
    }

    /**
     * @param cacheControl The {@code Cache-Control} header stored with the object, for example {@code max-age=3600}
     */
    public void setCacheControl(@Nullable String cacheControl) {
        this.cacheControl = cacheControl;
    }

    /**
     * @return Whether an existing object with the same key is replaced. Defaults to {@code true}, as the portable
     * upload contract requires.
     */
    public boolean isUpsert() {
        return upsert;
    }

    /**
     * @param upsert Whether an existing object with the same key is replaced. When {@code false} the upload of an
     * existing key fails with {@link SupabaseStorageException#KEY_ALREADY_EXISTS}.
     */
    public void setUpsert(boolean upsert) {
        this.upsert = upsert;
    }

    /**
     * @return User metadata stored with the object
     */
    @NonNull
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * @param metadata User metadata stored with the object
     */
    public void setMetadata(@NonNull Map<String, String> metadata) {
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(metadata, "metadata")));
    }

    /**
     * @return The object content
     */
    public byte @NonNull [] getBody() {
        return body;
    }

    /**
     * @param body The object content
     */
    public void setBody(byte @NonNull [] body) {
        this.body = Objects.requireNonNull(body, "body");
    }
}
