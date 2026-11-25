package io.micronaut.objectstorage.supabase

import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification

/**
 * Runs the shared module configuration TCK: per-storage and module-wide enabled flags.
 */
class SupabaseStorageModuleConfigurationSpec extends ObjectStorageConfigurationSpecification<SupabaseStorageOperations> {

    @Override
    String getConfigPrefix() {
        return SupabaseStorageConfiguration.PREFIX
    }

    @Override
    Class<SupabaseStorageOperations> getObjectStorage() {
        return SupabaseStorageOperations.class
    }
}
