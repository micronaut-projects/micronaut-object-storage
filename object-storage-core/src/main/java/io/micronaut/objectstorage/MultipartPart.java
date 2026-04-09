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
package io.micronaut.objectstorage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Portable multipart upload part metadata.
 *
 * @since 3.0.0
 */
public final class MultipartPart {

    private final int partNumber;
    private final String eTag;
    private final long size;
    @Nullable
    private final String checksum;

    /**
     * @param partNumber the positive part number
     * @param eTag the provider entity tag for the part
     * @param size the uploaded part size in bytes
     */
    public MultipartPart(int partNumber, @NonNull String eTag, long size) {
        this(partNumber, eTag, size, null);
    }

    /**
     * @param partNumber the positive part number
     * @param eTag the provider entity tag for the part
     * @param size the uploaded part size in bytes
     * @param checksum an optional portable checksum value
     */
    public MultipartPart(int partNumber, @NonNull String eTag, long size, @Nullable String checksum) {
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber must be greater than 0");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be greater than or equal to 0");
        }
        this.partNumber = partNumber;
        this.eTag = Objects.requireNonNull(eTag, "eTag");
        this.size = size;
        this.checksum = normalize(checksum);
    }

    /**
     * @return the positive part number
     */
    public int getPartNumber() {
        return partNumber;
    }

    /**
     * @return the provider entity tag for the part
     */
    @NonNull
    public String getETag() {
        return eTag;
    }

    /**
     * @return the uploaded part size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * @return an optional portable checksum value
     */
    @NonNull
    public Optional<String> getChecksum() {
        return Optional.ofNullable(checksum);
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
