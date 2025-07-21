package io.micronaut.objectstorage.request;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 *
 * An {@link UploadRequest} backed by a {@link InputStream}.
 * @since 2.9.1
 */
class InputStreamUploadRequest implements UploadRequest {

    private final InputStream inputStream;
    private final String key;
    private final String contentType;
    private final Long contentLength;
    private Map<String, String> metadata;

    InputStreamUploadRequest(InputStream inputStream, String key, String contentType, Long contentLength) {
        this.inputStream = inputStream;
        this.key = key;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Optional<Long> getContentSize() {
        return Optional.ofNullable(contentLength);
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }
}
