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

import io.micronaut.objectstorage.MultipartUploadHandle;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Multipart part upload request.
 *
 * <p>The supplied {@link UploadRequest} must target the same key as the multipart upload handle.</p>
 *
 * @since 3.0.0
 */
public final class UploadPartRequest {

    private final MultipartUploadHandle upload;
    private final int partNumber;
    private final UploadRequest uploadRequest;

    /**
     * @param upload the multipart upload handle
     * @param partNumber the positive part number
     * @param uploadRequest the payload upload request
     */
    public UploadPartRequest(@NonNull MultipartUploadHandle upload, int partNumber, @NonNull UploadRequest uploadRequest) {
        this.upload = Objects.requireNonNull(upload, "upload");
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber must be greater than 0");
        }
        this.uploadRequest = Objects.requireNonNull(uploadRequest, "uploadRequest");
        if (!upload.getKey().equals(uploadRequest.getKey())) {
            throw new IllegalArgumentException("uploadRequest key must match the multipart upload key");
        }
        this.partNumber = partNumber;
    }

    /**
     * @return the multipart upload handle
     */
    @NonNull
    public MultipartUploadHandle getUpload() {
        return upload;
    }

    /**
     * @return the positive part number
     */
    public int getPartNumber() {
        return partNumber;
    }

    /**
     * @return the payload upload request
     */
    @NonNull
    public UploadRequest getUploadRequest() {
        return uploadRequest;
    }
}
