package io.micronaut.objectstorage.supabase

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.IgnoreIf

/**
 * Runs the shared object storage TCK against a Supabase Storage container.
 */
@IgnoreIf({ !SupabaseStorageContainer.isDockerAvailable() })
@MicronautTest(startApplication = false)
class SupabaseStorageOperationsSpec extends AbstractSupabaseStorageSpec {
}
