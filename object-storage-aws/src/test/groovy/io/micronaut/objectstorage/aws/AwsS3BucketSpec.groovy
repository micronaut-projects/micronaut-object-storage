package io.micronaut.objectstorage.aws

import io.micronaut.context.BeanProvider
import io.micronaut.objectstorage.ObjectStorage
import io.micronaut.objectstorage.ObjectStorageSpecification
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

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3

@MicronautTest
class AwsS3BucketSpec extends ObjectStorageSpecification implements TestPropertyProvider {

    private static final String BUCKET_NAME = "test-bucket"

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack"), false)
            .withServices(S3)

    @Named("default")
    @Inject
    BeanProvider<AwsS3Bucket> awsS3Bucket

    @Inject
    S3Client s3

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        return [
                (AwsS3BucketConfiguration.PREFIX + ".default.name"): BUCKET_NAME,
                "aws.accessKeyId": localstack.getAccessKey(),
                "aws.secretKey": localstack.getSecretKey(),
                "aws.region": localstack.getRegion(),
                "aws.s3.endpoint-override": localstack.getEndpointOverride(S3)

        ]
    }

    void setup() {
        CreateBucketRequest bucketRequest = CreateBucketRequest.builder()
                .bucket(BUCKET_NAME)
                .build() as CreateBucketRequest

        s3.createBucket(bucketRequest)
    }

    @Override
    ObjectStorage getObjectStorage() {
        return awsS3Bucket.get()
    }
}
