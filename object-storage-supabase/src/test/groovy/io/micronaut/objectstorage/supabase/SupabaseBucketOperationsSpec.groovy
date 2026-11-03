package io.micronaut.objectstorage.supabase

import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.IgnoreIf

/**
 * Runs the shared bucket operations TCK against a Supabase Storage container.
 */
@IgnoreIf({ !SupabaseStorageContainer.isDockerAvailable() })
@MicronautTest(startApplication = false)
class SupabaseBucketOperationsSpec extends BucketOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "micronaut-object-storage-buckets-${System.currentTimeMillis()}"

    @Inject
    @Named('default')
    SupabaseStorageOperations supabaseStorage

    @Inject
    @Named('default')
    SupabaseBucketOperations supabaseBucketOperations

    void setup() {
        if (!supabaseBucketOperations.exists(BUCKET_NAME)) {
            supabaseBucketOperations.create(BUCKET_NAME)
        }
    }

    void cleanup() {
        AbstractSupabaseStorageSpec.emptyAndDeleteBucket(supabaseStorage, supabaseBucketOperations, BUCKET_NAME)
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return supabaseBucketOperations
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        return supabaseStorage
    }

    @Override
    Map<String, String> getProperties() {
        SupabaseStorageContainer.properties(BUCKET_NAME)
    }
}
