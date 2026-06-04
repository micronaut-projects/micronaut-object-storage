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

import io.micronaut.objectstorage.MultipartPart;
import io.micronaut.objectstorage.MultipartUploadHandle;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Multipart upload completion request.
 *
 * @since 3.0.1
 */
public final class CompleteMultipartUploadRequest {

    private final MultipartUploadHandle upload;
    private final List<MultipartPart> parts;

    /**
     * @param upload the multipart upload handle
     * @param parts the ordered part manifest to complete
     */
    public CompleteMultipartUploadRequest(@NonNull MultipartUploadHandle upload,
                                          @NonNull List<MultipartPart> parts) {
        this.upload = Objects.requireNonNull(upload, "upload");
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("parts must not be empty");
        }
        this.parts = validateOrdered(List.copyOf(parts));
    }

    /**
     * @return the multipart upload handle
     */
    @NonNull
    public MultipartUploadHandle getUpload() {
        return upload;
    }

    /**
     * @return the ordered part manifest
     */
    @NonNull
    public List<MultipartPart> getParts() {
        return parts;
    }

    @NonNull
    private static List<MultipartPart> validateOrdered(@NonNull List<MultipartPart> parts) {
        int previousPartNumber = 0;
        for (MultipartPart part : parts) {
            if (part.getPartNumber() <= previousPartNumber) {
                throw new IllegalArgumentException("parts must be strictly ordered by part number");
            }
            previousPartNumber = part.getPartNumber();
        }
        return parts;
    }
}
