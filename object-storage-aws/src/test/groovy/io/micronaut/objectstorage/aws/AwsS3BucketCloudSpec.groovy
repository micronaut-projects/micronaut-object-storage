package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Requires

@Requires({ env.AWS_ACCESS_KEY_ID && env.AWS_SECRET_ACCESS_KEY && env.AWS_REGION })
@MicronautTest
class AwsS3BucketCloudSpec extends AwsSpec implements TestPropertyProvider {}
