package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3

@MicronautTest
class AwsS3BucketLocalstackSpec extends AwsSpec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.0.3"))
            .withServices(S3)

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        return super.getProperties() + [
                "aws.accessKeyId": localstack.getAccessKey(),
                "aws.secretKey": localstack.getSecretKey(),
                "aws.region": localstack.getRegion(),
                "aws.s3.endpoint-override": localstack.getEndpointOverride(S3)

        ]
    }
}
