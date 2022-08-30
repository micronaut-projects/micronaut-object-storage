package io.micronaut.objectstorage.aws

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.See
import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotNull

@Property(name = "micronaut.object-storage.aws.pictures.bucket-name", value = "pictures-bucket")
@Property(name =  "micronaut.object-storage.aws.logos.bucket-name", value = "logos-bucket")
@Property(name = "spec.name", value = "AwsS3ConfigurationTest")
@MicronautTest(startApplication = false)
class AwsS3ConfigurationTest extends Specification {

    @Inject
    BeanContext beanContext

    void "there is one object mapper correctly qualified with the name"() {
        expect:
        beanContext.containsBean(ObjectStorageOperations, Qualifiers.byName("pictures"))
        beanContext.containsBean(ObjectStorageOperations, Qualifiers.byName("logos"))
        beanContext.containsBean(AwsS3Configuration, Qualifiers.byName("pictures"))

        when:
        AwsS3Configuration awsS3Configuration = beanContext.getBean(AwsS3Configuration, Qualifiers.byName("pictures"))

        then:
        'pictures' == awsS3Configuration.name
        'pictures-bucket' == awsS3Configuration.bucketName
    }

    @See("https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html")
    @Unroll("#description")
    void "bucket name validation"(String bucketName, String description) {
        given:
        MockService service = beanContext.getBean(MockService)

        when:
        AwsS3Configuration configuration = new AwsS3Configuration("foo")
        configuration.bucketName = bucketName
        service.validate(configuration)

        then:
        thrown(ConstraintViolationException)

        where:
        bucketName     | description
        'aa'           | 'Bucket names must be at least 3 characters long.'
        'a' * 64       | 'Bucket names must be max 63 characters long.'
        'aa*aa'        | 'Bucket names can consist only of lowercase letters, numbers, dots (.), and hyphens (-).'
        '-aaaa'        | 'Bucket names must begin and end with a letter or number.'
        'aa..aa'       | 'Bucket names must not contain two adjacent periods.'
        '192.168.5.4'  | 'Bucket names must not be formatted as an IP address (for example, 192.168.5.4).'
        'xn--aaa'      | 'Bucket names must not start with the prefix xn--.'
        'aaaa-s3alias' | 'Bucket names must not end with the suffix -s3alias.'
        'aaa.aa'       | "Buckets used with Amazon S3 Transfer Acceleration can't have dots (.) in their name"
    }

    void "verify valid bucket name"() {
        given:
        MockService service = beanContext.getBean(MockService)

        when:
        AwsS3Configuration configuration = new AwsS3Configuration("foo")
        configuration.bucketName = 'aaaaa'
        service.validate(configuration)

        then:
        noExceptionThrown()
    }

    static interface MockService {
        void validate(@NonNull @NotNull @Valid AwsS3Configuration configuration);
    }

    @Singleton
    @Requires(property = "spec.name", value = "AwsS3ConfigurationTest")
    static class DefaultMockService implements MockService {
        @Override
        void validate(@NonNull @NotNull @Valid AwsS3Configuration configuration) {

        }
    }

}
