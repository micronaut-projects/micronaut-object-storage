package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification

@Property(name = "spec.name", value = SPEC_NAME)
@Property(name = "micronaut.object-storage.local.default.enabled", value = "true")
@Property(name = "micronaut.object-storage.aws.default.enabled", value = "true")
@MicronautTest
class LocalStorageReactivePrimarySpec extends Specification {

    public static final String SPEC_NAME = 'LocalStorageReactivePrimarySpec'

    @Inject
    ApplicationContext ctx

    void "both local and aws reactive storage beans are enabled"() {
        when:
        def beans = ctx.getBeansOfType(ReactiveObjectStorageOperations)

        then:
        beans.size() == 2
    }

    void "one reactive storage bean is primary"() {
        when:
        def operations = ctx.getBean(ReactiveObjectStorageOperations)

        then:
        operations
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @MockBean(S3Client)
    S3Client s3Client() {
        return Mock(S3Client)
    }
}
