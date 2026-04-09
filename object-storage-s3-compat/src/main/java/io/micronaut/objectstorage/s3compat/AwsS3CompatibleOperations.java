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
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

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
        try {
            UploadRequest request = UploadRequest.fromBytes(inputStream.readAllBytes(), resolveStorageKey(key));
            if (contentType != null && !contentType.isBlank()) {
                request.setContentType(contentType);
            }
            return operations.upload(request);
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading request body for S3-compatible upload", e);
        }
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
            request.getContinuationToken().map(this::resolveStorageKey).orElse(null)
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

}
