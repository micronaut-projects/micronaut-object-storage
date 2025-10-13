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
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.PresignRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.Optional;

/**
 * Controller that materialises the fake pre-signed URLs produced by {@link LocalStorageOperations}.
 * <p>
 * It is intended for testing only and disabled by default. Enable with:
 * <pre>
 * micronaut.object-storage.local-presigned-request-controller=true
 * </pre>
 *
 * @since 2.10.0
 */
@Controller(LocalStorageOperations.LOCAL_PRESIGNED_REQUESTS_URL)
@Singleton
@Requires(condition = LocalPresignController.EnabledCondition.class)
@SuppressWarnings({
    // Logging tokens is not a problem because this is a test module.
    "java:S5145"
})
class LocalPresignController {
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
    public HttpResponse<StreamedFile> download(@NonNull String token) {
        Optional<LocalPresignStore.Entry> entryOpt = localPresignStore.consume(token);
        if (entryOpt.isEmpty()) {
            LOG.warn("Unable to find presigned request for token {}", token);
            return HttpResponse.notFound();
        }
        LocalPresignStore.Entry entry = entryOpt.get();
        if (entry.operation() != PresignRequest.Operation.DOWNLOAD) {
            LOG.warn("Illegal attempt to do a download using a pre-signed request that doesn't allow it, token is {}", token);
            return HttpResponse.status(HttpStatus.FORBIDDEN);
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
     * @param token   Opaque token.
     * @param bytes   Request body.
     * @param request HttpRequest to read headers.
     * @return 200 OK on success, 404/403 otherwise.
     */
    @Put("/{token}")
    @Consumes(MediaType.ALL)
    public HttpResponse<String> upload(@NonNull String token,
                                       @Body byte[] bytes,
                                       HttpRequest<?> request) {
        Optional<LocalPresignStore.Entry> entryOpt = localPresignStore.consume(token);
        if (entryOpt.isEmpty()) {
            LOG.warn("Unable to find presigned request for token {}", token);
            return HttpResponse.notFound();
        }
        LocalPresignStore.Entry entry = entryOpt.get();
        if (entry.operation() != PresignRequest.Operation.UPLOAD) {
            LOG.warn("Illegal attempt to do a download using a pre-signed request that doesn't allow it, token is {}", token);
            return HttpResponse.status(HttpStatus.FORBIDDEN);
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

    static class EnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getBean(LocalStorageModuleConfiguration.class).isPresignedRequestController();
        }
    }
}
