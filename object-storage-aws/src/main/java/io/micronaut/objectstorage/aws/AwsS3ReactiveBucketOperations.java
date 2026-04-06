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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Reactive AWS bucket operations.
 *
 * @since 3.0.0
 * @author Álvaro Sánchez-Mariscal
 */
@EachBean(AwsS3Configuration.class)
@Requires(beans = AwsS3Configuration.class)
@Requires(beans = S3AsyncClient.class)
public class AwsS3ReactiveBucketOperations implements ReactiveBucketOperations<HeadBucketResponse> {

    private final S3AsyncClient s3AsyncClient;

    public AwsS3ReactiveBucketOperations(S3AsyncClient s3AsyncClient) {
        this.s3AsyncClient = s3AsyncClient;
    }

    @Override
    @NonNull
    public Publisher<Void> create(@NonNull String name) {
        return Publishers.fromCompletableFuture(() -> handle(
            s3AsyncClient.createBucket(CreateBucketRequest.builder()
                .bucket(name)
                .build())
                .thenApply(ignored -> null),
            "Error when trying to create a bucket with name [%s] on Amazon S3",
            name
        ));
    }

    @Override
    @NonNull
    public Publisher<Optional<BucketEntry<HeadBucketResponse>>> retrieve(@NonNull String name) {
        return Publishers.fromCompletableFuture(() -> s3AsyncClient.headBucket(HeadBucketRequest.builder()
                .bucket(name)
                .build())
                .handle((response, throwable) -> {
                    if (throwable == null) {
                        return Optional.of(new BucketEntry<>(name, response));
                    }
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof NoSuchBucketException) {
                        return Optional.empty();
                    }
                    if (cause instanceof AwsServiceException || cause instanceof SdkClientException) {
                        String msg = String.format("Error when trying to retrieve a bucket with name [%s] from Amazon S3", name);
                        throw new CompletionException(new ObjectStorageException(msg, cause));
                    }
                    throw new CompletionException(cause);
                }));
    }

    @Override
    @NonNull
    public Publisher<Void> delete(@NonNull String name) {
        return Publishers.fromCompletableFuture(() -> handle(
            s3AsyncClient.deleteBucket(DeleteBucketRequest.builder()
                .bucket(name)
                .build())
                .thenApply(ignored -> null),
            "Error when trying to delete a bucket with name [%s] on Amazon S3",
            name
        ));
    }

    @Override
    @NonNull
    public Publisher<Boolean> exists(@NonNull String name) {
        return Publishers.fromCompletableFuture(() -> s3AsyncClient.headBucket(HeadBucketRequest.builder()
                .bucket(name)
                .build())
                .handle((response, throwable) -> {
                    if (throwable == null) {
                        return true;
                    }
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof NoSuchBucketException) {
                        return false;
                    }
                    if (cause instanceof AwsServiceException || cause instanceof SdkClientException) {
                        String msg = String.format("Error when trying to retrieve a bucket with name [%s] from Amazon S3", name);
                        throw new CompletionException(new ObjectStorageException(msg, cause));
                    }
                    throw new CompletionException(cause);
                }));
    }

    private <T> CompletableFuture<T> handle(CompletableFuture<T> future,
                                            String message,
                                            String bucketName) {
        return future.handle((response, throwable) -> {
            if (throwable == null) {
                return response;
            }
            Throwable cause = unwrap(throwable);
            if (cause instanceof AwsServiceException || cause instanceof SdkClientException) {
                throw new CompletionException(new ObjectStorageException(String.format(message, bucketName), cause));
            }
            throw new CompletionException(cause);
        });
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completionException && completionException.getCause() != null
            ? completionException.getCause()
            : throwable;
    }
}
