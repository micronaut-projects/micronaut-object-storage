package example

import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class ReactiveProfileService(private val objectStorage: ReactiveObjectStorageOperations<*, *, *>)
//end::class[]
