package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3

@MicronautTest(startApplication = false)
class AwsS3OperationsLocalstackSpec extends AbstractAwsS3Spec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
            .withServices(S3)

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        super.getProperties() + [
                'aws.accessKeyId'         : localstack.accessKey,
                'aws.secretKey'           : localstack.secretKey,
                'aws.region'              : localstack.region,
                'aws.services.s3.endpoint-override': localstack.getEndpointOverride(S3)
        ] as Map
    }
}
