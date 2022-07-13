/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.objectstorage.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Optional;

/**
 * @author Pavol Gressa
 */
@EachBean(AwsS3BucketConfiguration.class)
public class AwsS3Bucket implements ObjectStorage {

    private final S3Client s3Client;
    private final AwsS3BucketConfiguration bucketConfiguration;
    private final InputStreamMapper inputStreamMapper;

    public AwsS3Bucket(@Parameter AwsS3BucketConfiguration bucketConfiguration, S3Client s3Client, InputStreamMapper inputStreamMapper) {
        this.s3Client = s3Client;
        this.bucketConfiguration = bucketConfiguration;
        this.inputStreamMapper = inputStreamMapper;
    }

    @Override
    public UploadResponse put(UploadRequest uploadRequest) throws ObjectStorageException {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(bucketConfiguration.getName())
            .key(uploadRequest.getKey());

        uploadRequest.getContentType().ifPresent(builder::contentType);
        uploadRequest.getContentSize().ifPresent(builder::contentLength);

        PutObjectResponse putObjectResponse = null;
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
    public Optional<ObjectStorageEntry> get(String key) throws ObjectStorageException {
        GetObjectRequest.Builder builder = GetObjectRequest.builder();
        builder.bucket(bucketConfiguration.getName())
            .key(key);

        try {
            GetObjectRequest getObjectRequest = builder.build();
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);
            AwsS3ObjectStorageEntry entry = new AwsS3ObjectStorageEntry(key, responseInputStream);
            return Optional.of(entry);
        } catch (NoSuchKeyException noSuchKeyException) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) throws ObjectStorageException {
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        builder.bucket(bucketConfiguration.getName()).key(key);
        s3Client.deleteObject(builder.build());
    }
}
