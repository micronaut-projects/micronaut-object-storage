package io.micronaut.objectstorage.supabase

import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.supabase.storage.ObjectSchema
import io.micronaut.supabase.storage.UploadObjectResponse
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

/**
 * Runs the shared object storage TCK against the {@code default} Supabase storage; subclasses choose the environment.
 */
abstract class AbstractSupabaseStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "micronaut-object-storage-${System.currentTimeMillis()}"
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    SupabaseStorageOperations supabaseStorage

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    SupabaseBucketOperations supabaseBucketOperations

    void setup() {
        if (!supabaseBucketOperations.exists(BUCKET_NAME)) {
            supabaseBucketOperations.create(BUCKET_NAME)
        }
    }

    void cleanup() {
        emptyAndDeleteBucket(supabaseStorage, supabaseBucketOperations, BUCKET_NAME)
    }

    static void emptyAndDeleteBucket(SupabaseStorageOperations storage, SupabaseBucketOperations bucketOperations, String bucket) {
        if (bucketOperations.exists(bucket)) {
            storage.listObjects().each { storage.delete(it) }
            bucketOperations.delete(bucket)
        }
    }

    @Override
    ObjectStorageOperations<SupabaseStorageUploadRequest, UploadObjectResponse, List<ObjectSchema>> getObjectStorage() {
        return supabaseStorage
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        return supabaseBucketOperations
    }

    @Override
    boolean supportsPresignedUploadRoundTrip() {
        true
    }

    @Override
    Map<String, String> getProperties() {
        SupabaseStorageContainer.properties(BUCKET_NAME)
    }
}
