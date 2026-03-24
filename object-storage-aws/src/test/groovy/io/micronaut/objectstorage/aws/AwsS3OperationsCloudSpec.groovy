package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Requires

@MicronautTest
@Requires({
    env.AWS_ACCESS_KEY_ID &&
        env.AWS_SECRET_ACCESS_KEY &&
        env.AWS_REGION &&
        !System.getProperty('os.arch', '').contains('aarch64')
})
class AwsS3OperationsCloudSpec extends AbstractAwsS3Spec implements TestPropertyProvider {}
