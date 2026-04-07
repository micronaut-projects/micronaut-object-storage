package example

import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

//tag::beginclass[]
@Singleton
open class BucketService(
    @param:Named("default") private val bucketOperations: BucketOperations<*>,
    @param:Named("default") private val reactiveBucketOperations: ReactiveBucketOperations<*>
) {
//end::beginclass[]

    //tag::bucket-operations[]
    open fun manageBucket(name: String) {
        bucketOperations.create(name)
        val exists = bucketOperations.exists(name)
        bucketOperations.retrieve(name).ifPresent { bucket -> println(bucket.name()) }
        if (exists) {
            bucketOperations.delete(name)
        }
    }
    //end::bucket-operations[]

    //tag::reactive-bucket-operations[]
    open fun manageBucketReactive(name: String) {
        val create: Publisher<Void> = reactiveBucketOperations.create(name)
        val exists: Publisher<Boolean> = reactiveBucketOperations.exists(name)
    }
    //end::reactive-bucket-operations[]

//tag::endclass[]
}
//end::endclass[]
