from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.objectstorage.bucket import BucketOperations, ReactiveBucketOperations
from org.reactivestreams import Publisher


# tag::beginclass[]
@Singleton
class BucketService:

    def __init__(self,
                 bucket_operations: Annotated[BucketOperations, Named("default")],
                 reactive_bucket_operations: Annotated[ReactiveBucketOperations, Named("default")]):
        self.bucket_operations = bucket_operations
        self.reactive_bucket_operations = reactive_bucket_operations
# end::beginclass[]

    # tag::bucket-operations[]
    def manage_bucket(self, name: str) -> None:
        self.bucket_operations.create(name)
        exists = self.bucket_operations.exists(name)
        self.bucket_operations.retrieve(name).ifPresent(lambda bucket: print(bucket.name()))
        if exists:
            self.bucket_operations.delete(name)
    # end::bucket-operations[]

    # tag::reactive-bucket-operations[]
    def manage_bucket_reactive(self, name: str) -> None:
        create: Publisher = self.reactive_bucket_operations.create(name)
        exists: Publisher = self.reactive_bucket_operations.exists(name)
    # end::reactive-bucket-operations[]

# tag::endclass[]
# end::endclass[]
