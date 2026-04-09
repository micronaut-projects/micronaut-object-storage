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
package io.micronaut.objectstorage.tus;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Options;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.Requires;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP entrypoint for tus resumable uploads.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Controller("${micronaut.object-storage.tus.base-path:/tus}")
@Requires(property = TusModuleConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE)
public final class TusController {

    private final TusModuleConfiguration configuration;
    private final Map<String, TusUploadBackend> backends;

    public TusController(TusModuleConfiguration configuration,
                         Collection<TusUploadBackend> backends) {
        this.configuration = configuration;
        this.backends = new LinkedHashMap<>(backends.size());
        for (TusUploadBackend backend : backends) {
            this.backends.put(backend.getName(), backend);
        }
    }

    @Options(uri = "/{storageName}")
    public MutableHttpResponse<?> options(@PathVariable String storageName) {
        backend(storageName);
        return tus(HttpResponse.ok()
            .header(TusHeaders.VERSION_HEADER, TusHeaders.VERSION)
            .header(TusHeaders.EXTENSION_HEADER, TusHeaders.EXTENSIONS));
    }

    @Options(uri = "/{storageName}/{uploadId}")
    public MutableHttpResponse<?> optionsUpload(@PathVariable String storageName,
                                                @PathVariable String uploadId) {
        return options(storageName);
    }

    @Post(uri = "/{storageName}")
    public MutableHttpResponse<?> create(@PathVariable String storageName,
                                         @Header(TusHeaders.LENGTH) long uploadLength,
                                         @Header(TusHeaders.METADATA) @Nullable String encodedMetadata) {
        if (uploadLength < 0L) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Upload-Length must be zero or greater");
        }
        Map<String, String> metadata = decodeMetadata(encodedMetadata);
        String key = metadata.remove("key");
        if (key == null || key.isBlank()) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Upload-Metadata must contain a base64 encoded key entry");
        }
        String contentType = metadata.remove("contentType");
        TusUpload upload = backend(storageName).create(key, uploadLength, contentType, metadata);
        return tus(HttpResponse.status(HttpStatus.CREATED)
            .header("Location", configuration.getBasePath() + "/" + storageName + "/" + upload.id())
            .header(TusHeaders.OFFSET, Long.toString(upload.offset()))
            .header(TusHeaders.LENGTH, Long.toString(upload.uploadLength())));
    }

    @Head(uri = "/{storageName}/{uploadId}")
    public MutableHttpResponse<?> head(@PathVariable String storageName,
                                       @PathVariable String uploadId) {
        TusUpload upload = getUpload(storageName, uploadId);
        if (upload.aborted()) {
            return tus(HttpResponse.status(HttpStatus.GONE));
        }
        return tus(HttpResponse.noContent()
            .header(TusHeaders.OFFSET, Long.toString(upload.offset()))
            .header(TusHeaders.LENGTH, Long.toString(upload.uploadLength()))
            .header(TusHeaders.METADATA, encodeMetadata(upload)));
    }

    @Patch(uri = "/{storageName}/{uploadId}", consumes = "application/offset+octet-stream")
    public MutableHttpResponse<?> patch(@PathVariable String storageName,
                                        @PathVariable String uploadId,
                                        @Header(TusHeaders.OFFSET) long uploadOffset,
                                        @Body byte[] body) {
        if (body.length > configuration.getMaxChunkSize()) {
            throw new HttpStatusException(HttpStatus.REQUEST_ENTITY_TOO_LARGE, "Chunk exceeds configured tus max chunk size");
        }
        try {
            TusUpload upload = backend(storageName).append(uploadId, uploadOffset, body);
            return tus(HttpResponse.noContent()
                .header(TusHeaders.OFFSET, Long.toString(upload.offset()))
                .header(TusHeaders.LENGTH, Long.toString(upload.uploadLength())));
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (TusConflictException e) {
            throw new HttpStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (TusGoneException e) {
            throw new HttpStatusException(HttpStatus.GONE, e.getMessage());
        }
    }

    @Delete(uri = "/{storageName}/{uploadId}")
    public MutableHttpResponse<?> delete(@PathVariable String storageName,
                                         @PathVariable String uploadId) {
        try {
            backend(storageName).abort(uploadId);
            return tus(HttpResponse.noContent());
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (TusGoneException e) {
            throw new HttpStatusException(HttpStatus.GONE, e.getMessage());
        }
    }

    private TusUpload getUpload(String storageName, String uploadId) {
        return backend(storageName).find(uploadId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown upload: " + uploadId));
    }

    private TusUploadBackend backend(String storageName) {
        TusUploadBackend backend = backends.get(storageName);
        if (backend == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown object storage backend: " + storageName);
        }
        return backend;
    }

    private static Map<String, String> decodeMetadata(@Nullable String encodedMetadata) {
        if (encodedMetadata == null || encodedMetadata.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(TusMetadataCodec.decode(encodedMetadata));
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String encodeMetadata(TusUpload upload) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("key", upload.key());
        upload.getContentType().ifPresent(contentType -> metadata.put("contentType", contentType));
        metadata.putAll(upload.metadata());
        return TusMetadataCodec.encode(metadata);
    }

    private static MutableHttpResponse<?> tus(MutableHttpResponse<?> response) {
        return response.header(TusHeaders.RESUMABLE, TusHeaders.VERSION);
    }
}
