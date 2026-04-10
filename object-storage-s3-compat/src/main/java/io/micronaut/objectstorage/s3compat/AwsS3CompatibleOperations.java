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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.aws.AwsS3Configuration;
import io.micronaut.objectstorage.aws.AwsS3ObjectStorageEntry;
import io.micronaut.objectstorage.aws.AwsS3Operations;
import io.micronaut.http.HttpStatus;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * AWS S3 bridge for the S3-compatible transport layer.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
public final class AwsS3CompatibleOperations extends AbstractS3CompatibleOperations {

    private final AwsS3Configuration awsConfiguration;
    private final AwsS3Operations operations;
    private final S3Client s3Client;

    AwsS3CompatibleOperations(@NonNull S3CompatibilityConfiguration configuration,
                              @NonNull AwsS3Configuration awsConfiguration,
                              @NonNull AwsS3Operations operations,
                              @NonNull S3Client s3Client) {
        super(configuration);
        this.awsConfiguration = awsConfiguration;
        this.operations = operations;
        this.s3Client = s3Client;
    }

    @Override
    @NonNull
    public Optional<S3Object> getObject(@NonNull String key) {
        return operations.retrieve(resolveStorageKey(key))
            .map(entry -> toObject(stripBasePath(entry.getKey()), entry));
    }

    @Override
    @NonNull
    public UploadResponse<?> putObject(@NonNull String key,
                                       @NonNull InputStream inputStream,
                                       @Nullable Long contentLength,
                                       @Nullable String contentType) {
        return operations.upload(new S3CompatibilityUploadRequest(
            inputStream,
            resolveStorageKey(key),
            contentLength,
            contentType
        ));
    }

    @Override
    public void deleteObject(@NonNull String key) {
        operations.delete(resolveStorageKey(key));
    }

    @Override
    @NonNull
    public S3ListResponse listObjects(@NonNull ListObjectsRequest request) {
        ListObjectsRequest storageRequest = new ListObjectsRequest(
            request.getPageSize(),
            resolveListPrefix(request.getPrefix().orElse(null)),
            request.getContinuationToken().orElse(null)
        );
        io.micronaut.objectstorage.response.ListObjectsResponse response = operations.listObjects(storageRequest);
        List<S3ObjectSummary> objects = response.getKeys().stream()
            .map(this::toObjectSummary)
            .toList();
        return new S3ListResponse(
            objects,
            response.getContinuationToken().map(this::stripBasePath).orElse(null)
        );
    }

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    @NonNull
    public S3MultipartUpload createMultipartUpload(@NonNull String key, @Nullable String contentType) {
        CreateMultipartUploadRequest.Builder request = CreateMultipartUploadRequest.builder()
            .bucket(awsConfiguration.getBucket())
            .key(resolveStorageKey(key));
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        try {
            return new S3MultipartUpload(s3Client.createMultipartUpload(request.build()).uploadId());
        } catch (AwsServiceException e) {
            throw s3Exception(e);
        } catch (SdkClientException e) {
            throw new ObjectStorageException("Error initiating multipart upload for key [" + key + ']', e);
        }
    }

    @Override
    @NonNull
    public S3MultipartPart uploadPart(@NonNull String key,
                                      @NonNull String uploadId,
                                      int partNumber,
                                      @NonNull InputStream inputStream,
                                      @Nullable Long contentLength) {
        UploadPartRequest request = UploadPartRequest.builder()
            .bucket(awsConfiguration.getBucket())
            .key(resolveStorageKey(key))
            .uploadId(uploadId)
            .partNumber(partNumber)
            .build();
        try {
            UploadPartResponse response = s3Client.uploadPart(
                request,
                contentLength != null
                    ? RequestBody.fromInputStream(inputStream, contentLength)
                    : RequestBody.fromBytes(inputStream.readAllBytes())
            );
            return new S3MultipartPart(partNumber, response.eTag(), contentLength, null);
        } catch (AwsServiceException e) {
            throw s3Exception(e);
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading multipart request body for key [" + key + ']', e);
        } catch (SdkClientException e) {
            throw new ObjectStorageException("Error uploading multipart part for key [" + key + ']', e);
        }
    }

    @Override
    @NonNull
    public S3MultipartListPartsResponse listParts(@NonNull String key,
                                                  @NonNull String uploadId,
                                                  @Nullable Integer partNumberMarker,
                                                  int maxParts) {
        ListPartsRequest.Builder request = ListPartsRequest.builder()
            .bucket(awsConfiguration.getBucket())
            .key(resolveStorageKey(key))
            .uploadId(uploadId)
            .maxParts(maxParts);
        if (partNumberMarker != null) {
            request.partNumberMarker(partNumberMarker);
        }
        try {
            var response = s3Client.listParts(request.build());
            return new S3MultipartListPartsResponse(
                response.parts().stream()
                    .map(part -> new S3MultipartPart(
                        part.partNumber(),
                        part.eTag(),
                        part.size(),
                        part.lastModified()
                    ))
                    .toList(),
                response.isTruncated() ? response.nextPartNumberMarker() : null,
                response.isTruncated()
            );
        } catch (AwsServiceException e) {
            throw s3Exception(e);
        } catch (SdkClientException e) {
            throw new ObjectStorageException("Error listing multipart parts for key [" + key + ']', e);
        }
    }

    @Override
    @NonNull
    public S3MultipartCompletedUpload completeMultipartUpload(@NonNull String key,
                                                              @NonNull String uploadId,
                                                              @NonNull List<S3CompletedPart> completedParts) {
        List<CompletedPart> parts = completedParts.stream()
            .map(part -> CompletedPart.builder()
                .partNumber(part.partNumber())
                .eTag(part.eTag())
                .build())
            .toList();
        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
            .bucket(awsConfiguration.getBucket())
            .key(resolveStorageKey(key))
            .uploadId(uploadId)
            .multipartUpload(upload -> upload.parts(parts))
            .build();
        try {
            return new S3MultipartCompletedUpload(s3Client.completeMultipartUpload(request).eTag());
        } catch (AwsServiceException e) {
            throw s3Exception(e);
        } catch (SdkClientException e) {
            throw new ObjectStorageException("Error completing multipart upload for key [" + key + ']', e);
        }
    }

    @Override
    public void abortMultipartUpload(@NonNull String key, @NonNull String uploadId) {
        AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
            .bucket(awsConfiguration.getBucket())
            .key(resolveStorageKey(key))
            .uploadId(uploadId)
            .build();
        try {
            s3Client.abortMultipartUpload(request);
        } catch (AwsServiceException e) {
            throw s3Exception(e);
        } catch (SdkClientException e) {
            throw new ObjectStorageException("Error aborting multipart upload for key [" + key + ']', e);
        }
    }

    @NonNull
    private S3Object toObject(@NonNull String key, @NonNull AwsS3ObjectStorageEntry entry) {
        return new S3Object(
            key,
            entry,
            entry.getNativeEntry().contentLength(),
            entry.getNativeEntry().lastModified()
        );
    }

    @NonNull
    private S3ObjectSummary toObjectSummary(@NonNull String storageKey) {
        HeadObjectResponse response = headObject(storageKey);
        return new S3ObjectSummary(
            stripBasePath(storageKey),
            response.contentLength(),
            response.lastModified()
        );
    }

    @NonNull
    private HeadObjectResponse headObject(@NonNull String storageKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(awsConfiguration.getBucket())
                .key(storageKey)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            throw new ObjectStorageException("Error reading object metadata from Amazon S3 for key [" + storageKey + ']', e);
        }
    }

    @NonNull
    private static S3CompatibilityException s3Exception(@NonNull AwsServiceException e) {
        String code = e.awsErrorDetails() != null && e.awsErrorDetails().errorCode() != null
            ? e.awsErrorDetails().errorCode()
            : "InternalError";
        String message = e.awsErrorDetails() != null && e.awsErrorDetails().errorMessage() != null
            ? e.awsErrorDetails().errorMessage()
            : e.getMessage();
        return new S3CompatibilityException(HttpStatus.valueOf(e.statusCode()), code, message);
    }

}
