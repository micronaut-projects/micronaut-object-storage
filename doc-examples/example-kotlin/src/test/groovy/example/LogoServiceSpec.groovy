package example

import io.micronaut.context.env.Environment
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

@MicronautTest(environments = Environment.AMAZON_EC2)
class LogoServiceSpec extends Specification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "pictures-bucket"

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
            .withServices("s3")

    @Inject
    @Named("pictures")
    ObjectStorageOperations<?, ?, ?> pictures

    @Inject
    LogoService logoService

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        localstack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", BUCKET_NAME)
        return [
                "micronaut.object-storage.aws.pictures.bucket": BUCKET_NAME,
                "aws.accessKeyId": localstack.getAccessKey(),
                "aws.secretKey": localstack.getSecretKey(),
                "aws.region": localstack.getRegion(),
                "aws.services.s3.endpoint-override": localstack.getEndpoint().toString()
        ]
    }

    void "it resolves objects by URI"() {
        given:
        pictures.upload(UploadRequest.fromBytes("micronaut".bytes, "avatars/logo.png"))

        when:
        Optional<InputStream> logo = logoService.retrieveLogo()

        then:
        logo.present
        logo.get().text == "micronaut"
    }
}
