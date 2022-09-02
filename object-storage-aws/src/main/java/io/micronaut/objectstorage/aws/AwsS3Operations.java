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
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.FileUploadRequest;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * AWS implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(AwsS3Configuration.class)
public class AwsS3Operations implements ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse> {

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
    public PutObjectResponse upload(@NonNull UploadRequest uploadRequest) {
        PutObjectRequest objectRequest = getRequestBuilder(uploadRequest).build();
        RequestBody requestBody = getRequestBody(uploadRequest);
        return s3Client.putObject(objectRequest, requestBody);
    }

    @Override
    @NonNull
    public PutObjectResponse upload(@NonNull UploadRequest request,
                                    @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        RequestBody requestBody = getRequestBody(request);
        return s3Client.putObject(builder.build(), requestBody);
    }

    @Override
    @NonNull
    public Optional<ObjectStorageEntry> retrieve(@NonNull String key) {
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
            AwsS3ObjectStorageEntry entry = new AwsS3ObjectStorageEntry(key, responseInputStream);
            return Optional.of(entry);
        } catch (NoSuchKeyException noSuchKeyException) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(@NonNull String key) throws ObjectStorageException {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(configuration.getBucket())
            .key(key)
            .build());
    }

    /**
     * Creates an AWS' {@link PutObjectRequest.Builder} from a Micronaut's {@link UploadRequest}
     */
    @NonNull
    protected PutObjectRequest.Builder getRequestBuilder(@NonNull UploadRequest request) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(configuration.getBucket())
            .key(request.getKey());

        request.getContentType().ifPresent(builder::contentType);
        request.getContentSize().ifPresent(builder::contentLength);
        return builder;
    }

    /**
     * Creates an AWS' {@link RequestBody} from a Micronaut's {@link UploadRequest}
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
