package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorageException
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AwsS3ReactiveBucketOperationsSpec extends Specification {

    void "reactive AWS bucket operations use the async client"() {
        given:
        S3AsyncClient client = Mock()
        AwsS3ReactiveBucketOperations operations = new AwsS3ReactiveBucketOperations(client)
        HeadBucketResponse response = HeadBucketResponse.builder().build()

        when:
        await(operations.create('reports'))
        def retrieved = await(operations.retrieve('reports'))
        boolean exists = await(operations.exists('reports'))
        await(operations.delete('reports'))

        then:
        retrieved.present
        retrieved.get().name() == 'reports'
        retrieved.get().nativeEntry() == response
        exists
        1 * client.createBucket(_) >> CompletableFuture.completedFuture(null)
        1 * client.headBucket(_) >> CompletableFuture.completedFuture(response)
        1 * client.headBucket(_) >> CompletableFuture.completedFuture(response)
        1 * client.deleteBucket(_) >> CompletableFuture.completedFuture(null)
    }

    void "reactive AWS bucket operations preserve empty bucket lookups and wrapped client errors"() {
        given:
        S3AsyncClient client = Mock()
        AwsS3ReactiveBucketOperations operations = new AwsS3ReactiveBucketOperations(client)
        CompletableFuture<HeadBucketResponse> missing = new CompletableFuture<>()
        missing.completeExceptionally(NoSuchBucketException.builder().message('missing').build())
        CompletableFuture<Void> failure = new CompletableFuture<>()
        failure.completeExceptionally(NoSuchBucketException.builder().message('boom').build())

        when:
        def retrieved = await(operations.retrieve('reports'))
        boolean exists = await(operations.exists('reports'))
        await(operations.create('reports'))

        then:
        !retrieved.present
        !exists
        def ex = thrown(ObjectStorageException)
        ex.message == 'Error when trying to create a bucket with name [reports] on Amazon S3'
        1 * client.headBucket(_) >> missing
        1 * client.headBucket(_) >> missing
        1 * client.createBucket(_) >> failure
    }

    private static <T> T await(Publisher<T> publisher) {
        AtomicReference<T> value = new AtomicReference<>()
        AtomicReference<Throwable> error = new AtomicReference<>()
        CountDownLatch latch = new CountDownLatch(1)

        publisher.subscribe(new Subscriber<T>() {
            @Override
            void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(T element) {
                value.set(element)
            }

            @Override
            void onError(Throwable throwable) {
                error.set(throwable)
                latch.countDown()
            }

            @Override
            void onComplete() {
                latch.countDown()
            }
        })

        assert latch.await(5, TimeUnit.SECONDS)
        if (error.get() != null) {
            Throwable throwable = error.get()
            if (throwable instanceof CompletionException && throwable.cause != null) {
                throw throwable.cause
            }
            throw throwable
        }
        return value.get()
    }
}
