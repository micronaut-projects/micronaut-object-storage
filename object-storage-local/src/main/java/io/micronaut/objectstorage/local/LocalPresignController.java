/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.PresignRequest;
import io.micronaut.objectstorage.request.UploadRequest;

import java.io.InputStream;
import java.net.URLConnection;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Controller that materialises the fake pre-signed URLs produced by {@link LocalStorageOperations}.
 * <p>
 * It is intended for testing only and disabled by default. Enable with:
 * <pre>
 * micronaut.object-storage.local-presigned-request-controller=true
 * </pre>
 *
 * @since 2.10
 */
@Controller(LocalPresignController.LOCAL_PRESIGNED_REQUESTS_URL)
@Singleton
@Requires(property = "micronaut.object-storage.local-presigned-request-controller", value = "true")
class LocalPresignController {
    static final String LOCAL_PRESIGNED_REQUESTS_URL = "/mn-os/local";
    private static final Logger LOG = LoggerFactory.getLogger(LocalPresignController.class);
    private final LocalStorageOperations operations;
    private final LocalPresignStore localPresignStore;

    LocalPresignController(LocalStorageOperations operations, LocalPresignStore localPresignStore) {
        this.operations = operations;
        this.localPresignStore = localPresignStore;
    }

    /**
     * Handles a download operation.
     *
     * @param token Opaque token.
     * @return The streamed file or 404/403 in case of error.
     */
    @Get("/{token}")
    public HttpResponse<?> download(@NonNull String token) {
        Optional<LocalPresignStore.Entry> entryOpt = localPresignStore.consume(token);
        if (entryOpt.isEmpty()) {
            return notFound(token);
        }
        LocalPresignStore.Entry entry = entryOpt.get();
        if (entry.operation() != PresignRequest.Operation.DOWNLOAD) {
            return illegal(token);
        }

        return operations.retrieve(entry.key())
            .map(storageEntry -> {
                InputStream in = storageEntry.getInputStream();
                String contentType = storageEntry.getContentType()
                    .orElse(URLConnection.guessContentTypeFromName(entry.key()));
                StreamedFile file = new StreamedFile(in, MediaType.of(contentType));
                return HttpResponse.ok(file);
            })
            .orElseGet(HttpResponse::notFound);
    }

    /**
     * Handles an upload operation.
     *
     * @param token  Opaque token.
     * @param bytes  Request body.
     * @param request HttpRequest to read headers.
     * @return 200 OK on success, 404/403 otherwise.
     */
    @Put("/{token}")
    @Consumes(MediaType.ALL)
    public HttpResponse<?> upload(@NonNull String token,
                                  @Body @Nullable byte[] bytes,
                                  HttpRequest<?> request) {
        Optional<LocalPresignStore.Entry> entryOpt = localPresignStore.consume(token);
        if (entryOpt.isEmpty()) {
            return notFound(token);
        }
        LocalPresignStore.Entry entry = entryOpt.get();
        if (entry.operation() != PresignRequest.Operation.UPLOAD) {
            return illegal(token);
        }

        if (bytes == null) {
            return HttpResponse.badRequest("Missing body");
        }

        UploadRequest uploadRequest = UploadRequest.fromBytes(bytes, entry.key());
        request.getContentType()
            .map(MediaType::toString)
            .ifPresent(uploadRequest::setContentType);

        try {
            operations.upload(uploadRequest);
            return HttpResponse.ok();
        } catch (ObjectStorageException ex) {
            return HttpResponse.serverError();
        }
    }

    private static MutableHttpResponse<Object> illegal(String token) {
        LOG.warn("Illegal attempt to do a download using a pre-signed request that doesn't allow it, token is {}", token);
        return HttpResponse.status(HttpStatus.FORBIDDEN);
    }

    private static MutableHttpResponse<Object> notFound(String token) {
        LOG.warn("Unable to find presigned request for token {}", token);
        return HttpResponse.notFound();
    }
}
