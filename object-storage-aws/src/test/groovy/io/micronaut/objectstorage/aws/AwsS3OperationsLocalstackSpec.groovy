package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3
import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

@MicronautTest(startApplication = false)
@IgnoreIf({ env.AWS_ACCESS_KEY_ID && env.AWS_SECRET_ACCESS_KEY && env.AWS_REGION })
class AwsS3OperationsLocalstackSpec extends AbstractAwsS3Spec implements TestPropertyProvider {
    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
            .withServices(S3)

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        def props = [
                'aws.accessKeyId'         : localstack.accessKey,
                'aws.secretAccessKey'     : localstack.secretKey,
                'aws.region'              : localstack.region,
                'aws.services.s3.endpoint-override': localstack.getEndpointOverride(S3)
        ] as Map
        // AWS SDK requires these in the system props to authenticate.
        props.forEach { String k, Object v -> System.setProperty(k, v.toString()) }
        super.getProperties() + props
    }
}
