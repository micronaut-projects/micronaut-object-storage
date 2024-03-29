package example

import io.micronaut.context.env.Environment
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

@MicronautTest(environments = Environment.AMAZON_EC2)
class ProfileServiceSpec extends Specification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "profile-pictures-bucket"

    public static final String OBJECT_STORAGE_NAME = "default"
    public static final String USER_ID = "user123"

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.3.1"))
            .withServices(LocalStackContainer.Service.S3)

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AwsS3Operations awsS3Bucket

    @Inject
    S3Client s3

    @Inject
    ProfileService service

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        localstack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", BUCKET_NAME)
        return [
                "aws.accessKeyId": localstack.getAccessKey(),
                "aws.secretKey": localstack.getSecretKey(),
                "aws.region": localstack.getRegion(),
                "aws.services.s3.endpoint-override": localstack.getEndpointOverride(LocalStackContainer.Service.S3)

        ]
    }

    void "it works"() {
        given:
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        String fileName = path.toFile().name

        expect:
        !service.retrieveProfilePicture(USER_ID, fileName).present

        when:
        def saveResult = service.saveProfilePicture(USER_ID, path)

        then:
        saveResult

        when:
        def retrieveResult = service.retrieveProfilePicture(USER_ID, fileName)

        then:
        retrieveResult.present
        retrieveResult.get().toFile().text == "micronaut"

        when:
        service.deleteProfilePicture(USER_ID, fileName)

        then:
        !service.retrieveProfilePicture(USER_ID, fileName).present
    }

}
