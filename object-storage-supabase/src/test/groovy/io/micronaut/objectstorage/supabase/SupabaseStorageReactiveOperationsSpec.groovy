package io.micronaut.objectstorage.supabase

import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.IgnoreIf

/**
 * Runs the shared reactive object storage TCK against a Supabase Storage container.
 */
@IgnoreIf({ !SupabaseStorageContainer.isDockerAvailable() })
@MicronautTest(startApplication = false)
class SupabaseStorageReactiveOperationsSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "micronaut-object-storage-reactive-${System.currentTimeMillis()}"

    @Inject
    @Named('default')
    ReactiveObjectStorageOperations<?, ?, ?> reactiveStorage

    @Inject
    @Named('default')
    ReactiveBucketOperations<?> reactiveBucketOperations

    @Inject
    @Named('default')
    SupabaseStorageOperations supabaseStorage

    @Inject
    @Named('default')
    SupabaseBucketOperations supabaseBucketOperations

    void setup() {
        if (!awaitOne(reactiveBucketOperations.exists(BUCKET_NAME))) {
            awaitCompletion(reactiveBucketOperations.create(BUCKET_NAME))
        }
    }

    void cleanup() {
        AbstractSupabaseStorageSpec.emptyAndDeleteBucket(supabaseStorage, supabaseBucketOperations, BUCKET_NAME)
    }

    @Override
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return reactiveStorage
    }

    @Override
    Map<String, String> getProperties() {
        SupabaseStorageContainer.properties(BUCKET_NAME)
    }
}
