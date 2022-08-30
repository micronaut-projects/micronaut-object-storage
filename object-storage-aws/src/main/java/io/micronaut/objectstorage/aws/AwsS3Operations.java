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
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.UploadRequest;
import io.micronaut.objectstorage.UploadResponse;
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

/**
 * AWS implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(AwsS3Configuration.class)
public class AwsS3Operations implements ObjectStorageOperations {

    private final S3Client s3Client;
    private final AwsS3Configuration bucketConfiguration;
    private final InputStreamMapper inputStreamMapper;

    public AwsS3Operations(@Parameter AwsS3Configuration bucketConfiguration,
                           S3Client s3Client,
                           InputStreamMapper inputStreamMapper) {
        this.s3Client = s3Client;
        this.bucketConfiguration = bucketConfiguration;
        this.inputStreamMapper = inputStreamMapper;
    }

    @Override
    public UploadResponse upload(UploadRequest uploadRequest) throws ObjectStorageException {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(bucketConfiguration.getName())
            .key(uploadRequest.getKey());

        uploadRequest.getContentType().ifPresent(builder::contentType);
        uploadRequest.getContentSize().ifPresent(builder::contentLength);

        PutObjectResponse putObjectResponse;
        if (uploadRequest instanceof UploadRequest.FileUploadRequest) {
            UploadRequest.FileUploadRequest fileUploadRequest = (UploadRequest.FileUploadRequest) uploadRequest;
            putObjectResponse = s3Client.putObject(builder.build(), fileUploadRequest.getFile().toPath());
        } else {
            byte[] inputBytes = inputStreamMapper.toByteArray(uploadRequest.getInputStream());
            putObjectResponse = s3Client.putObject(builder.build(), RequestBody.fromBytes(inputBytes));
        }

        return new UploadResponse.Builder().withETag(putObjectResponse.eTag()).build();
    }

    @Override
    public Optional<ObjectStorageEntry> retrieve(String key) throws ObjectStorageException {
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketConfiguration.getName())
                .key(key)
                .build());
            AwsS3ObjectStorageEntry entry = new AwsS3ObjectStorageEntry(key, responseInputStream);
            return Optional.of(entry);
        } catch (NoSuchKeyException noSuchKeyException) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucketConfiguration.getName())
            .key(key)
            .build());
    }
}
