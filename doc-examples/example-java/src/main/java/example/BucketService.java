package example;

import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

//tag::beginclass[]
@Singleton
public class BucketService {

    private final BucketOperations<?> bucketOperations;
    private final ReactiveBucketOperations<?> reactiveBucketOperations;

    public BucketService(@Named("default") BucketOperations<?> bucketOperations,
                         @Named("default") ReactiveBucketOperations<?> reactiveBucketOperations) {
        this.bucketOperations = bucketOperations;
        this.reactiveBucketOperations = reactiveBucketOperations;
    }
//end::beginclass[]

    //tag::bucket-operations[]
    public void manageBucket(String name) {
        bucketOperations.create(name);
        boolean exists = bucketOperations.exists(name);
        bucketOperations.retrieve(name).ifPresent(bucket -> {
            System.out.println(bucket.name());
        });
        if (exists) {
            bucketOperations.delete(name);
        }
    }
    //end::bucket-operations[]

    //tag::reactive-bucket-operations[]
    public void manageBucketReactive(String name) {
        Publisher<Void> create = reactiveBucketOperations.create(name);
        Publisher<Boolean> exists = reactiveBucketOperations.exists(name);
    }
    //end::reactive-bucket-operations[]

//tag::endclass[]
}
//end::endclass[]
