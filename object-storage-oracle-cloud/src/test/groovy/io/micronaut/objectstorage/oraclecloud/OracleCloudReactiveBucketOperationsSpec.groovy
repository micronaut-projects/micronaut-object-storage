package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorageAsync
import com.oracle.bmc.objectstorage.responses.GetBucketResponse
import io.micronaut.objectstorage.ObjectStorageException
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

class OracleCloudReactiveBucketOperationsSpec extends Specification {

    void "reactive OCI bucket operations use the async client"() {
        given:
        OracleCloudStorageConfiguration configuration = new OracleCloudStorageConfiguration('default')
        configuration.namespace = 'ns'
        configuration.compartmentId = 'compartment'
        GetBucketResponse response = Mock()
        AtomicInteger getBucketCalls = new AtomicInteger()
        ObjectStorageAsync client = [
            createBucket: { request, handler ->
                handler.onSuccess(request, null)
                return Stub(Future)
            },
            getBucket: { request, handler ->
                getBucketCalls.incrementAndGet()
                handler.onSuccess(request, response)
                return Stub(Future)
            },
            deleteBucket: { request, handler ->
                handler.onSuccess(request, null)
                return Stub(Future)
            }
        ] as ObjectStorageAsync
        OracleCloudReactiveBucketOperations operations = new OracleCloudReactiveBucketOperations(configuration, client)

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
        getBucketCalls.get() == 2
    }

    void "reactive OCI bucket operations keep 404 lookups empty and wrap failures"() {
        given:
        OracleCloudStorageConfiguration configuration = new OracleCloudStorageConfiguration('default')
        configuration.namespace = 'ns'
        configuration.compartmentId = 'compartment'
        AtomicInteger getBucketCalls = new AtomicInteger()
        AtomicInteger deleteCalls = new AtomicInteger()
        ObjectStorageAsync client = [
            getBucket: { request, handler ->
                getBucketCalls.incrementAndGet()
                handler.onError(request, new BmcException(404, 'NotFound', 'missing', null))
                return Stub(Future)
            },
            deleteBucket: { request, handler ->
                deleteCalls.incrementAndGet()
                handler.onError(request, new BmcException(500, 'InternalError', 'boom', null))
                return Stub(Future)
            }
        ] as ObjectStorageAsync
        OracleCloudReactiveBucketOperations operations = new OracleCloudReactiveBucketOperations(configuration, client)

        when:
        def retrieved = await(operations.retrieve('reports'))
        boolean exists = await(operations.exists('reports'))
        await(operations.delete('reports'))

        then:
        retrieved == null || !retrieved.present
        !exists
        def ex = thrown(ObjectStorageException)
        ex.message == 'Error deleting bucket with name [reports] in namespace [ns]'
        getBucketCalls.get() == 2
        deleteCalls.get() == 1
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
