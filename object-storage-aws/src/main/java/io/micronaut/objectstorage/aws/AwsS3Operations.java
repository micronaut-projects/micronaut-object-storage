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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.FileUploadRequest;
import io.micronaut.objectstorage.request.PresignRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.PresignResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import jakarta.inject.Inject;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
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
@Requires(condition = ToggeableCondition.class)
@Requires(beans = AwsS3Configuration.class)
public class AwsS3Operations implements ObjectStorageOperations<
    PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {

    private final S3Client s3Client;
    private final AwsS3Configuration configuration;
    private final InputStreamMapper inputStreamMapper;
    private final S3Presigner presigner;

    /**
     * Constructs an AwsS3Operations.
     *
     * @param configuration     AWS S3 Configuration.
     * @param s3Client          S3 Client.
     * @param inputStreamMapper InputStream Mapper.
     * @param environment       If non-null, used to read the {@code aws.region} property for presigning.
     */
    @Inject
    public AwsS3Operations(@Parameter AwsS3Configuration configuration,
                           S3Client s3Client,
                           InputStreamMapper inputStreamMapper,
                           @Nullable Environment environment) {
        this.s3Client = s3Client;
        this.configuration = configuration;
        this.inputStreamMapper = inputStreamMapper;
        S3Presigner presigner = null;
        if (environment != null) {
            String region = environment.getProperty("aws.region", String.class, (String) null);
            URI override = environment.getProperty("aws.services.s3.endpoint-override", URI.class, (URI) null);

            if (region != null || override != null) {
                S3Presigner.Builder builder = S3Presigner.builder().s3Client(s3Client);
                if (region != null) {
                    builder.region(Region.of(region));
                }
                if (override != null) {
                    builder.endpointOverride(override);
                }
                presigner = builder.build();
            }
        }
        this.presigner = presigner;
    }

    /**
     * @deprecated Use {@link #AwsS3Operations(AwsS3Configuration, S3Client, InputStreamMapper, Environment)}.
     *
     * @param configuration AWS S3 Configuration.
     * @param s3Client S3 Client.
     * @param inputStreamMapper InputStream Mapper.
     */
    @Deprecated(since = "2.10.0", forRemoval = true)
    public AwsS3Operations(@Parameter AwsS3Configuration configuration,
                           S3Client s3Client,
                           InputStreamMapper inputStreamMapper) {
        this(configuration, s3Client, inputStreamMapper, null);
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

    @Override
    @NonNull
    public PresignResponse presign(@NonNull PresignRequest request) {
        if (presigner == null) {
            throw new UnsupportedOperationException("Pre-signed requests require 'aws.region' configuration");
        }
        Duration duration = request.getExpiresIn().orElse(Duration.ofHours(1));
        try {
            switch (request.getOperation()) {
                case DOWNLOAD -> {
                    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(configuration.getBucket())
                        .key(request.getKey())
                        .build();
                    PresignedGetObjectRequest presigned = presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                            .signatureDuration(duration)
                            .getObjectRequest(getObjectRequest)
                            .build());
                    return new PresignResponse(java.net.URI.create(presigned.url().toString()), presigned.expiration());
                }
                case UPLOAD -> {
                    PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                        .bucket(configuration.getBucket())
                        .key(request.getKey());
                    request.getContentType().ifPresent(putBuilder::contentType);
                    request.getContentLength().ifPresent(putBuilder::contentLength);
                    PresignedPutObjectRequest presigned = presigner.presignPutObject(
                        PutObjectPresignRequest.builder()
                            .signatureDuration(duration)
                            .putObjectRequest(putBuilder.build())
                            .build());
                    return new PresignResponse(java.net.URI.create(presigned.url().toString()), presigned.expiration());
                }
                default -> throw new UnsupportedOperationException("Unsupported presign operation: " + request.getOperation());
            }
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error generating presigned %s URL for key [%s] in Amazon S3",
                request.getOperation(), request.getKey());
            throw new ObjectStorageException(msg, e);
        }
    }
}
