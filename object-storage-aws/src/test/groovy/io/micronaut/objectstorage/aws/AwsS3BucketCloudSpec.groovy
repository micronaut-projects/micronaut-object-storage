package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

@MicronautTest
@Requires({ env.AWS_ACCESS_KEY_ID && env.AWS_SECRET_ACCESS_KEY && env.AWS_REGION })
class AwsS3BucketCloudSpec extends AbstractAwsS3BucketSpec {
}
