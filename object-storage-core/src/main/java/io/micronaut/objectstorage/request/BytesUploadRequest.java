package io.micronaut.objectstorage.request;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Optional;

/**
 * Upload request implementation using byte array.
 */
public class BytesUploadRequest implements UploadRequest {

    @NonNull
    private final byte[] bytes;

    @Nullable
    private String contentType;

    @NonNull
    private final String key;

    public BytesUploadRequest(@NonNull byte[] bytes, @NonNull String key) {
        this.bytes = bytes;
        this.key = key;
        this.contentType = URLConnection.guessContentTypeFromName(key);
    }

    public BytesUploadRequest(@NonNull byte[] bytes,
                              @NonNull String key,
                              @NonNull String contentType) {
        this.bytes = bytes;
        this.key = key;
        this.contentType = contentType;
    }

    @Override
    @NonNull
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    @NonNull
    public String getKey() {
        return key;
    }

    @Override
    @NonNull
    public Optional<Long> getContentSize() {
        return Optional.of((long) bytes.length);
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @NonNull
    public byte[] getBytes() {
        return bytes;
    }
}
