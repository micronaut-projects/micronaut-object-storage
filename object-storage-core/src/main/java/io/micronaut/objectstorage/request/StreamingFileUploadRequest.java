package io.micronaut.objectstorage.request;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.StreamingFileUpload;

/**
 * 
 * An {@link UploadRequest} backed by a {@link StreamingFileUpload}.
 */
public class StreamingFileUploadRequest implements UploadRequest {

    @NonNull
    private final StreamingFileUpload streamingFileUpload;
    @NonNull
    private final String key;

    @NonNull
    private Map<String, String> metadata;
    

    public StreamingFileUploadRequest(@NonNull StreamingFileUpload streamingFileUpload) {
        this(streamingFileUpload, streamingFileUpload.getName(), Collections.emptyMap());
    }

    public StreamingFileUploadRequest(@NonNull StreamingFileUpload streamingFileUpload, @NonNull String key) {
        this(streamingFileUpload, key, Collections.emptyMap());
    }

    public StreamingFileUploadRequest(@NonNull StreamingFileUpload streamingFileUpload, @NonNull String key, @NonNull Map<String, String> metadata) {
        this.streamingFileUpload = streamingFileUpload;
        this.key = key;
        this.metadata = metadata;
    }

    @NonNull
    @Override
    public Optional<String> getContentType() {
        return streamingFileUpload.getContentType()
            .map(MediaType::getName);
    }

    @NonNull
    @Override
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public Optional<Long> getContentSize() {
        return Optional.of(streamingFileUpload.getSize());
    }
    

    @NonNull
    @Override
    public InputStream getInputStream() {
    	return streamingFileUpload.asInputStream();
    }

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public void setMetadata(@NonNull Map<String, String> metadata) {
        this.metadata = metadata;
    }
}