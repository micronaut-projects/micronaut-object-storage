package io.micronaut.objectstorage.local

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification

@Property(name = "spec.name", value = SPEC_NAME)
@Property(name = "micronaut.object-storage.aws.source-storage.enabled", value = "false")
@Property(name = "micronaut.object-storage.aws.source-storage.bucket", value = "some-production-bucket")
@Property(name = "micronaut.object-storage.local.source-storage.enabled", value = "true")
@MicronautTest
class LocalStorageReactiveNamedSpec extends Specification {

    public static final String SPEC_NAME = 'LocalStorageReactiveNamedSpec'

    @Inject
    @Named("source-storage")
    ReactiveObjectStorageOperations<?, ?, ?> storageOperations

    void "it can inject named reactive storage operations"() {
        expect:
        storageOperations
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @MockBean(S3Client)
    S3Client s3Client() {
        return Mock(S3Client)
    }
}
