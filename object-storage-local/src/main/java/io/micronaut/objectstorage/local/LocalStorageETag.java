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

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class LocalStorageETag {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private LocalStorageETag() {
    }

    @NonNull
    static String transferTo(@NonNull InputStream inputStream, @NonNull OutputStream outputStream) throws IOException {
        MessageDigest digest = newSha256Digest();
        DigestOutputStream digestOutputStream = new DigestOutputStream(outputStream, digest);
        inputStream.transferTo(digestOutputStream);
        digestOutputStream.flush();
        return HEX_FORMAT.formatHex(digest.digest());
    }

    @NonNull
    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

}
