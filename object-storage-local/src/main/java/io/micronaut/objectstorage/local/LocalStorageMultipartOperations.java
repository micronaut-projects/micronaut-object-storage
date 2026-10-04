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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadResponse;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadResponse;
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest;
import io.micronaut.objectstorage.multipart.ListMultipartPartsResponse;
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations;
import io.micronaut.objectstorage.multipart.MultipartPart;
import io.micronaut.objectstorage.multipart.MultipartUploadHandle;
import io.micronaut.objectstorage.multipart.UploadPartRequest;
import io.micronaut.objectstorage.multipart.UploadPartResponse;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Local multipart upload operations.
 *
 * @author Ayoub Ait Abdellah
 * @since 3.1.0
 */
@EachBean(LocalStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = LocalStorageConfiguration.class)
@Requires(beans = LocalStorageOperations.class)
@Primary
final class LocalStorageMultipartOperations implements MultipartObjectStorageOperations<
    LocalStorageMultipartUpload,
    LocalStorageOperations.LocalStorageFile,
    LocalStorageOperations.LocalStorageFile> {

    private final LocalStorageOperations objectStorageOperations;
    private final LocalStorageMultipartUploadState uploadState;
    private final LocalStorageMetadataMode metadataMode;

    LocalStorageMultipartOperations(@Parameter LocalStorageConfiguration configuration,
                                    LocalStorageOperations objectStorageOperations) {
        LocalStorageLayout layout = new LocalStorageLayout(configuration);
        this.objectStorageOperations = objectStorageOperations;
        this.uploadState = new LocalStorageMultipartUploadState(layout);
        this.metadataMode = configuration.getMetadataMode();
    }

    @Override
    @NonNull
    public CreateMultipartUploadResponse<LocalStorageMultipartUpload> createMultipartUpload(@NonNull CreateMultipartUploadRequest request) {
        LocalStorageOperations.validateMetadata(metadataMode, request.getMetadata());
        String uploadId = UUID.randomUUID().toString();
        Path uploadPath = uploadState.createUpload(request, uploadId);
        return CreateMultipartUploadResponse.of(
            new MultipartUploadHandle(request.getKey(), uploadId),
            new LocalStorageMultipartUpload(uploadPath)
        );
    }

    @Override
    @NonNull
    public UploadPartResponse<LocalStorageOperations.LocalStorageFile> uploadPart(@NonNull UploadPartRequest request) {
        LocalStorageMultipartUploadState.UploadSession session = uploadState.retrieveUploadSession(request.getUpload());
        LocalStorageMultipartUploadState.StoredMultipartPart storedPart = uploadState.storePart(
            session.path(),
            request.getPartNumber(),
            request.getUploadRequest().getInputStream()
        );
        return UploadPartResponse.of(
            storedPart.part(),
            new LocalStorageOperations.LocalStorageFile(storedPart.path())
        );
    }

    @Override
    @NonNull
    public ListMultipartPartsResponse listParts(@NonNull ListMultipartPartsRequest request) {
        LocalStorageMultipartUploadState.UploadSession session = uploadState.retrieveUploadSession(request.getUpload());
        int partNumberMarker = LocalStorageMultipartContinuationTokens.decode(request).orElse(0);
        List<MultipartPart> parts = uploadState.retrieveMultipartParts(session.path()).stream()
            .filter(part -> part.getPartNumber() > partNumberMarker)
            .limit((long) request.getPageSize() + 1)
            .toList();
        if (parts.size() <= request.getPageSize()) {
            return new ListMultipartPartsResponse(parts);
        }
        List<MultipartPart> page = parts.subList(0, request.getPageSize());
        MultipartPart lastPart = page.get(page.size() - 1);
        return new ListMultipartPartsResponse(page, LocalStorageMultipartContinuationTokens.encode(request, lastPart.getPartNumber()));
    }

    @Override
    @NonNull
    public CompleteMultipartUploadResponse<LocalStorageOperations.LocalStorageFile> completeMultipartUpload(
        @NonNull CompleteMultipartUploadRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        LocalStorageMultipartUploadState.UploadSession session = uploadState.retrieveUploadSession(upload);
        Path completedFile = uploadState.buildCompletedMultipartFile(session.path(), request.getParts(), upload.getUploadId());
        try {
            var uploadRequest = new LocalCompletedMultipartUploadRequest(
                upload.getKey(),
                session.contentType(),
                completedFile,
                session.metadata()
            );
            var uploadResponse = objectStorageOperations.upload(uploadRequest);
            uploadState.markMultipartUploadCompleted(session.path());
            uploadState.deleteMultipartUploadAfterCompletion(session.path());
            return CompleteMultipartUploadResponse.of(upload, uploadResponse.getETag(), uploadResponse.getNativeResponse());
        } catch (RuntimeException e) {
            uploadState.deleteIfExists(completedFile, e);
            throw e;
        }
    }

    @Override
    public void abortMultipartUpload(@NonNull AbortMultipartUploadRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        Path uploadPath = uploadState.multipartUploadPath(upload.getUploadId());
        uploadState.retrieveAbortProperties(uploadPath).ifPresent(properties -> uploadState.validateUploadSessionKey(upload, properties));
        uploadState.deleteMultipartUpload(uploadPath);
    }
}
