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
package io.micronaut.objectstorage.db.internal;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Counts and digests the bytes of an upload as the store reads them, and enforces the maximum object size.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class DigestingInputStream extends FilterInputStream {

    private final MessageDigest digest = newMd5();
    private final long maxSize;
    private long count;

    /**
     * @param in The content.
     * @param maxSize The maximum number of bytes, or {@code null} for no limit.
     */
    public DigestingInputStream(@NonNull InputStream in, @Nullable Long maxSize) {
        super(in);
        this.maxSize = maxSize == null ? Long.MAX_VALUE : maxSize;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            digest.update((byte) b);
            advance(1);
        }
        return b;
    }

    @Override
    public int read(byte @NonNull [] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            digest.update(b, off, n);
            advance(n);
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("skip is not supported while digesting");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Reads and digests whatever the store left unread, so that the count and digest cover the whole content.
     *
     * @throws IOException If reading fails.
     */
    public void drain() throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        do {
            read = read(buffer, 0, buffer.length);
        } while (read >= 0);
    }

    /**
     * @return The number of bytes read.
     */
    public long getCount() {
        return count;
    }

    /**
     * @return The MD5 digest of the bytes read.
     */
    public byte @NonNull [] getDigest() {
        return digest.digest();
    }

    /**
     * @param digest An MD5 digest.
     * @return The digest in hexadecimal, the entity tag format of the storage.
     */
    @NonNull
    public static String toHex(byte @NonNull [] digest) {
        return HexFormat.of().formatHex(digest);
    }

    /**
     * @return A new MD5 digest.
     */
    @NonNull
    public static MessageDigest newMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 digest is not available", e);
        }
    }

    private void advance(int n) throws IOException {
        count += n;
        if (count > maxSize) {
            throw new ObjectTooLargeException(maxSize);
        }
    }

    /**
     * Thrown when an upload exceeds the maximum object size.
     */
    public static final class ObjectTooLargeException extends IOException {
        /**
         * @param maxSize The maximum size.
         */
        public ObjectTooLargeException(long maxSize) {
            super("The object exceeds the maximum object size of " + maxSize + " bytes");
        }
    }
}
