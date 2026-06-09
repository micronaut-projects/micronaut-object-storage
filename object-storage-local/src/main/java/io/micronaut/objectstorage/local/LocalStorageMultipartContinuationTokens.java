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
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest;
import io.micronaut.objectstorage.multipart.MultipartUploadHandle;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.StringJoiner;

final class LocalStorageMultipartContinuationTokens {

    private static final String MULTIPART_CONTINUATION_TOKEN_PREFIX = "local-multipart:v1:";

    private LocalStorageMultipartContinuationTokens() {
    }

    static Optional<Integer> decode(ListMultipartPartsRequest request) {
        String continuationToken = request.getContinuationToken().orElse(null);
        if (continuationToken == null || continuationToken.isEmpty()) {
            return Optional.empty();
        }
        try {
            Token decodedToken = Token.decode(continuationToken);
            if (!decodedToken.matches(request)) {
                throw new ObjectStorageException("Local multipart continuation token does not match the current request");
            }
            return Optional.of(decodedToken.partNumberMarker());
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid local multipart continuation token", e);
        }
    }

    static String encode(ListMultipartPartsRequest request, int partNumberMarker) {
        MultipartUploadHandle upload = request.getUpload();
        String payload = new StringJoiner("\n")
            .add(encodeTokenPart(upload.getKey()))
            .add(encodeTokenPart(upload.getUploadId()))
            .add(encodeTokenPart(Integer.toString(request.getPageSize())))
            .add(encodeTokenPart(Integer.toString(partNumberMarker)))
            .toString();
        return MULTIPART_CONTINUATION_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeTokenPart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeTokenPart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static ObjectStorageException invalidMultipartContinuationToken() {
        return new ObjectStorageException("Invalid local multipart continuation token");
    }

    private record Token(@NonNull String key,
                         @NonNull String uploadId,
                         int pageSize,
                         int partNumberMarker) {

        private static Token decode(String continuationToken) {
            if (!continuationToken.startsWith(MULTIPART_CONTINUATION_TOKEN_PREFIX)) {
                throw invalidMultipartContinuationToken();
            }
            String decodedToken = new String(
                Base64.getUrlDecoder().decode(continuationToken.substring(MULTIPART_CONTINUATION_TOKEN_PREFIX.length())),
                StandardCharsets.UTF_8
            );
            String[] parts = decodedToken.split("\n", -1);
            if (parts.length != 4) {
                throw invalidMultipartContinuationToken();
            }
            int partNumberMarker = Integer.parseInt(decodeTokenPart(parts[3]));
            if (partNumberMarker <= 0) {
                throw invalidMultipartContinuationToken();
            }
            return new Token(
                decodeTokenPart(parts[0]),
                decodeTokenPart(parts[1]),
                Integer.parseInt(decodeTokenPart(parts[2])),
                partNumberMarker
            );
        }

        private boolean matches(ListMultipartPartsRequest request) {
            MultipartUploadHandle upload = request.getUpload();
            return upload.getKey().equals(key)
                && upload.getUploadId().equals(uploadId)
                && request.getPageSize() == pageSize;
        }
    }
}
