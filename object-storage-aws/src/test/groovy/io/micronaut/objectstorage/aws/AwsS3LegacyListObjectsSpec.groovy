package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

@MicronautTest(startApplication = false)
class AwsS3LegacyListObjectsSpec extends AbstractAwsS3Spec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
        .withServices("s3")

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        super.getProperties() + [
                'aws.accessKeyId'                : localstack.accessKey,
                'aws.secretKey'                  : localstack.secretKey,
                'aws.region'                     : localstack.region,
                'aws.services.s3.endpoint-override': localstack.getEndpoint().toString()
        ] as Map
    }

    void 'legacy listObjects exhausts paginated AWS listing across multiple native pages'() {
        given:
        List<String> keys = (1..1005).collect { index -> String.format('bulk/item-%04d.txt', index) }
        keys.each { awsS3Bucket.upload(UploadRequest.fromBytes(TEXT.bytes, it, CONTENT_TYPE)) }

        when:
        def firstPage = awsS3Bucket.listObjects(new ListObjectsRequest(1000))
        def secondPage = awsS3Bucket.listObjects(new ListObjectsRequest(1000, null, firstPage.continuationToken.orElse(null)))
        def listedKeys = awsS3Bucket.listObjects()

        then:
        firstPage.keys.size() == 1000
        firstPage.keys == keys.take(1000)
        firstPage.continuationToken.present
        secondPage.keys == keys.drop(1000)
        !secondPage.continuationToken.present
        listedKeys instanceof LinkedHashSet
        listedKeys.size() == keys.size()
        listedKeys as List == keys

        cleanup:
        keys.each {
            awsS3Bucket.delete(it)
        }
    }
}
