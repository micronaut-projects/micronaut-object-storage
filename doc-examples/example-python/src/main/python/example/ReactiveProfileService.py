from jakarta.inject import Singleton
from micronaut.objectstorage import ReactiveObjectStorageOperations


# tag::class[]
@Singleton
class ReactiveProfileService:

    def __init__(self, object_storage: ReactiveObjectStorageOperations):
        self.object_storage = object_storage
# end::class[]
