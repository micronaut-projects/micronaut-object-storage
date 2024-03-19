/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import jakarta.inject.Singleton;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * AWS bucket operations.
 *
 * @since 2.2.0
 * @author Jonas Konrad
 */
@Singleton
public class AwsS3BucketOperations implements BucketOperations<
    PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {
    private final S3Client s3Client;
    private final InputStreamMapper inputStreamMapper;

    public AwsS3BucketOperations(S3Client s3Client, InputStreamMapper inputStreamMapper) {
        this.s3Client = s3Client;
        this.inputStreamMapper = inputStreamMapper;
    }

    @Override
    public void createBucket(String name) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(name)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to create a bucket with name [%s] on Amazon S3", name);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void deleteBucket(String name) {
        try {
            s3Client.deleteBucket(DeleteBucketRequest.builder()
                .bucket(name)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to delete a bucket with name [%s] on Amazon S3", name);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public Set<String> listBuckets() {
        try {
            return s3Client.listBuckets().buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toSet());
        } catch (AwsServiceException | SdkClientException e) {
            throw new ObjectStorageException("Error when trying to list buckets on Amazon S3", e);
        }
    }

    @Override
    public ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> storageForBucket(String bucket) {
        AwsS3Configuration cfg = new AwsS3Configuration("");
        cfg.setBucket(bucket);
        return new AwsS3Operations(cfg, s3Client, inputStreamMapper);
    }
}
