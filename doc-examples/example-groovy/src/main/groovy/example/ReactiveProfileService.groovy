package example

import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class ReactiveProfileService {

    private final ReactiveObjectStorageOperations<?, ?, ?> objectStorage

    ReactiveProfileService(ReactiveObjectStorageOperations<?, ?, ?> objectStorage) {
        this.objectStorage = objectStorage
    }
}
//end::class[]
