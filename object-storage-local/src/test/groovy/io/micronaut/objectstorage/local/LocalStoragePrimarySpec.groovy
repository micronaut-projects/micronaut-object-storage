package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification

@Property(name = "spec.name", value = SPEC_NAME)
@Property(name = "micronaut.object-storage.local.default.enabled", value = "true")
@Property(name = "micronaut.object-storage.aws.default.enabled", value = "true")
@MicronautTest
class LocalStoragePrimarySpec extends Specification {

    public static final String SPEC_NAME = 'LocalStoragePrimarySpec'

    @Inject
    ApplicationContext ctx

    void "both local and aws storage are enabled"() {
        when:
        def beans = ctx.getBeansOfType(ObjectStorageOperations)

        then:
        beans.size() == 2
    }

    void "local storage is primary"() {
        when:
        def operations = ctx.getBean(ObjectStorageOperations)

        then:
        operations instanceof LocalStorageOperations
    }

    void "local multipart storage is primary"() {
        when:
        def beans = ctx.getBeansOfType(MultipartObjectStorageOperations)
        def operations = ctx.getBean(MultipartObjectStorageOperations)

        then:
        beans.size() == 2
        beans.any { it instanceof LocalStorageMultipartOperations }
        operations instanceof LocalStorageMultipartOperations
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @MockBean(S3Client)
    S3Client s3Client() {
        return Mock(S3Client)
    }

}
