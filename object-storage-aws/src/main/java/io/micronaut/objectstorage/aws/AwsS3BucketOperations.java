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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.util.Optional;

/**
 * AWS bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(AwsS3Configuration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = AwsS3Configuration.class)
public class AwsS3BucketOperations implements BucketOperations<HeadBucketResponse> {
    private final S3Client s3Client;

    public AwsS3BucketOperations(@Parameter AwsS3Configuration configuration,
                                 S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public void create(@NonNull String name) {
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
    @NonNull
    public Optional<BucketEntry<HeadBucketResponse>> retrieve(@NonNull String name) {
        try {
            HeadBucketResponse response = s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(name)
                .build());
            return Optional.of(new BucketEntry<>(name, response));
        } catch (NoSuchBucketException e) {
            return Optional.empty();
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to retrieve a bucket with name [%s] from Amazon S3", name);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void delete(@NonNull String name) {
        try {
            s3Client.deleteBucket(DeleteBucketRequest.builder()
                .bucket(name)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to delete a bucket with name [%s] on Amazon S3", name);
            throw new ObjectStorageException(msg, e);
        }
    }
}
