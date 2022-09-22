/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.FileUploadRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AWS implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(AwsS3Configuration.class)
public class AwsS3Operations implements ObjectStorageOperations<
    PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {

    private final S3Client s3Client;
    private final AwsS3Configuration configuration;
    private final InputStreamMapper inputStreamMapper;

    /**
     *
     * @param configuration AWS S3 Configuration
     * @param s3Client S3 Client
     * @param inputStreamMapper InputStream Mapper
     */
    public AwsS3Operations(@Parameter AwsS3Configuration configuration,
                           S3Client s3Client,
                           InputStreamMapper inputStreamMapper) {
        this.s3Client = s3Client;
        this.configuration = configuration;
        this.inputStreamMapper = inputStreamMapper;
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest uploadRequest) {
        PutObjectRequest objectRequest = getRequestBuilder(uploadRequest).build();
        RequestBody requestBody = getRequestBody(uploadRequest);
        try {
            PutObjectResponse response = s3Client.putObject(objectRequest, requestBody);
            return UploadResponse.of(uploadRequest.getKey(), response.eTag(), response);
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to upload a file with key [%s] to Amazon S3", uploadRequest.getKey());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest request,
                                    @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        RequestBody requestBody = getRequestBody(request);
        try {
            PutObjectResponse response = s3Client.putObject(builder.build(), requestBody);
            return UploadResponse.of(request.getKey(), response.eTag(), response);
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to upload a file with key [%s] to AWS S3", request.getKey());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<AwsS3ObjectStorageEntry> retrieve(@NonNull String key) {
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
            AwsS3ObjectStorageEntry entry = new AwsS3ObjectStorageEntry(key, responseInputStream);
            return Optional.of(entry);
        } catch (NoSuchKeyException noSuchKeyException) {
            return Optional.empty();
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to retrieve a file with key [%s] from Amazon S3", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public DeleteObjectResponse delete(@NonNull String key) {
        try {
            return s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to delete a file with key [%s] from Amazon S3", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public boolean exists(@NonNull String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
            return true;
        } catch (NoSuchKeyException noSuchKeyException) {
            return false;
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to check the existence of a file with key [%s] in Amazon S3", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @NonNull
    @Override
    public Set<String> listObjects() {
        String bucket = configuration.getBucket();
        try {
            ListObjectsResponse response = s3Client.listObjects(b -> b.bucket(bucket));
            return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toSet());
        } catch (NoSuchBucketException e) {
            return Collections.emptySet();
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when listing the objects of the bucket [%s] in Amazon S3", bucket);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(configuration.getBucket())
                .destinationBucket(configuration.getBucket())
                .sourceKey(sourceKey)
                .destinationKey(destinationKey)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to copy a file from key [%s] to key [%s] in Amazon S3", sourceKey, destinationKey);
            throw new ObjectStorageException(msg, e);
        }
    }

    /**
     * @param request the upload request
     * @return An AWS' {@link PutObjectRequest.Builder} from a Micronaut's {@link UploadRequest}.
     */
    @NonNull
    protected PutObjectRequest.Builder getRequestBuilder(@NonNull UploadRequest request) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(configuration.getBucket())
            .key(request.getKey());

        request.getContentType().ifPresent(builder::contentType);
        request.getContentSize().ifPresent(builder::contentLength);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            builder.metadata(request.getMetadata());
        }
        return builder;
    }

    /**
     * @param uploadRequest the upload request
     * @return An AWS' {@link RequestBody} from a Micronaut's {@link UploadRequest}.
     */
    @NonNull
    protected RequestBody getRequestBody(@NonNull UploadRequest uploadRequest) {
        if (uploadRequest instanceof FileUploadRequest) {
            FileUploadRequest request = (FileUploadRequest) uploadRequest;
            return RequestBody.fromFile(request.getFile());
        } else if (uploadRequest instanceof BytesUploadRequest) {
            BytesUploadRequest request = (BytesUploadRequest) uploadRequest;
            return RequestBody.fromBytes(request.getBytes());
        } else {
            byte[] inputBytes = inputStreamMapper.toByteArray(uploadRequest.getInputStream());
            return RequestBody.fromBytes(inputBytes);
        }
    }
}
