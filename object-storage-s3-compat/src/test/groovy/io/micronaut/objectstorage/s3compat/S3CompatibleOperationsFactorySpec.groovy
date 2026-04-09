package io.micronaut.objectstorage.s3compat

import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.local.LocalStorageOperations
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification

class S3CompatibleOperationsFactorySpec extends Specification {

    void 'it auto-detects the local adapter when that is the only matching provider'() {
        given:
        BeanContext beanContext = Mock()
        LocalStorageOperations localStorageOperations = Mock()
        def factory = new S3CompatibleOperationsFactory(beanContext)
        def configuration = configuration('assets', 'default', null)

        when:
        def operations = factory.s3CompatibleOperations(configuration)

        then:
        operations instanceof LocalStorageS3CompatibleOperations
        1 * beanContext.findBean(LocalStorageOperations, Qualifiers.byName('default')) >> Optional.of(localStorageOperations)
        1 * beanContext.findBean(AwsS3Operations, Qualifiers.byName('default')) >> Optional.empty()
        0 * _
    }

    void 'it uses the configured aws provider when requested explicitly'() {
        given:
        BeanContext beanContext = Mock()
        AwsS3Operations awsOperations = Mock()
        AwsS3Configuration awsConfiguration = new AwsS3Configuration('default')
        awsConfiguration.setBucket('backing-bucket')
        S3Client s3Client = Mock()
        def factory = new S3CompatibleOperationsFactory(beanContext)
        def configuration = configuration('assets', 'default', S3CompatibilityStorageProvider.AWS)

        when:
        def operations = factory.s3CompatibleOperations(configuration)

        then:
        operations instanceof AwsS3CompatibleOperations
        1 * beanContext.findBean(LocalStorageOperations, Qualifiers.byName('default')) >> Optional.empty()
        1 * beanContext.findBean(AwsS3Operations, Qualifiers.byName('default')) >> Optional.of(awsOperations)
        1 * beanContext.getBean(AwsS3Configuration, Qualifiers.byName('default')) >> awsConfiguration
        1 * beanContext.getBean(S3Client) >> s3Client
        0 * _
    }

    void 'it requires a provider hint when local and aws storages share the same name'() {
        given:
        BeanContext beanContext = Mock()
        def factory = new S3CompatibleOperationsFactory(beanContext)
        def configuration = configuration('assets', 'default', null)

        when:
        factory.s3CompatibleOperations(configuration)

        then:
        def e = thrown(ConfigurationException)
        e.message.contains('storage-provider')
        1 * beanContext.findBean(LocalStorageOperations, Qualifiers.byName('default')) >> Optional.of(Mock(LocalStorageOperations))
        1 * beanContext.findBean(AwsS3Operations, Qualifiers.byName('default')) >> Optional.of(Mock(AwsS3Operations))
        0 * _
    }

    private static S3CompatibilityConfiguration configuration(String bucket,
                                                              String storage,
                                                              S3CompatibilityStorageProvider provider) {
        def configuration = new S3CompatibilityConfiguration(bucket)
        configuration.setStorage(storage)
        configuration.setStorageProvider(provider)
        configuration
    }
}
