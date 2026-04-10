package io.micronaut.objectstorage.aws

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

@MicronautTest(startApplication = false)
class AwsS3OperationsLocalstackSpec extends AbstractAwsS3Spec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE).asCompatibleSubstituteFor("localstack/localstack"))
            .withServices("s3")

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        super.getProperties() + [
                'aws.accessKeyId'         : localstack.accessKey,
                'aws.secretKey'           : localstack.secretKey,
                'aws.region'              : localstack.region,
                'aws.services.s3.endpoint-override': localstack.getEndpoint().toString()
        ] as Map
    }

    @Override
    boolean supportsPresignedUploadRoundTrip() {
        true
    }
}
