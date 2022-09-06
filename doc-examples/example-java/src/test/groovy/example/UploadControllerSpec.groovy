package example

import io.micronaut.context.annotation.Property
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

@MicronautTest(environments = Environment.AMAZON_EC2)
class UploadControllerSpec extends Specification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "profile-pictures-bucket"
    public static final String OBJECT_STORAGE_NAME = "default"

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.0.3"))
            .withServices(LocalStackContainer.Service.S3)

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AwsS3Operations awsS3Bucket

    @Inject
    S3Client s3

    @Inject
    @Client('/')
    HttpClient client

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        return [
                "aws.accessKeyId": localstack.getAccessKey(),
                "aws.secretKey": localstack.getSecretKey(),
                "aws.region": localstack.getRegion(),
                "aws.s3.endpoint-override": localstack.getEndpointOverride(LocalStackContainer.Service.S3)

        ]
    }

    void setup() {
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build() as CreateBucketRequest)
    }

    void "it works"() {
        given:
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        File file = path.toFile()
        MultipartBody requestBody = MultipartBody
                .builder()
                .addPart("fileUpload", file.name, MediaType.TEXT_PLAIN_TYPE, file)
                .build()
        HttpRequest request = HttpRequest
                .POST("/", requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)


        when:
        def response = client.toBlocking().exchange(request, String)

        then:
        response.status() == HttpStatus.CREATED
        response.body.present
        response.header("ETag")
    }

}
