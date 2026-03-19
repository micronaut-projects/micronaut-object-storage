package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3

@MicronautTest(startApplication = false)
class AwsS3PaginationSpec extends AbstractAwsS3Spec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
        .withServices(S3)

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        super.getProperties() + [
                'aws.accessKeyId'                : localstack.accessKey,
                'aws.secretKey'                  : localstack.secretKey,
                'aws.region'                     : localstack.region,
                'aws.services.s3.endpoint-override': localstack.getEndpointOverride(S3)
        ] as Map
    }

    void 'paginated listing uses native AWS continuation tokens and preserves AWS response order'() {
        given:
        List<String> keys = [
                'animals/cat.txt',
                'animals/dog.txt',
                'animals/mammals/fox.txt',
                'plants/oak.txt',
                'plants/pine.txt',
        ]
        keys.each { awsS3Bucket.upload(UploadRequest.fromBytes(TEXT.bytes, it, CONTENT_TYPE)) }

        when:
        def firstPage = awsS3Bucket.listObjects(new ListObjectsRequest(2))
        def secondPage = awsS3Bucket.listObjects(new ListObjectsRequest(2, null, firstPage.continuationToken.orElse(null)))
        def finalPage = awsS3Bucket.listObjects(new ListObjectsRequest(2, null, secondPage.continuationToken.orElse(null)))

        then:
        firstPage.keys == ['animals/cat.txt', 'animals/dog.txt']
        firstPage.continuationToken.present
        secondPage.keys == ['animals/mammals/fox.txt', 'plants/oak.txt']
        secondPage.continuationToken.present
        finalPage.keys == ['plants/pine.txt']
        !finalPage.continuationToken.present

        when:
        def prefixedFirstPage = awsS3Bucket.listObjects(new ListObjectsRequest(2, 'animals/'))
        def prefixedSecondPage = awsS3Bucket.listObjects(new ListObjectsRequest(2, 'animals/', prefixedFirstPage.continuationToken.orElse(null)))

        then:
        prefixedFirstPage.keys == ['animals/cat.txt', 'animals/dog.txt']
        prefixedFirstPage.continuationToken.present
        prefixedSecondPage.keys == ['animals/mammals/fox.txt']
        !prefixedSecondPage.continuationToken.present

        cleanup:
        keys.each {
            awsS3Bucket.delete(it)
        }
    }

    void 'replaying the first AWS page returns the same Micronaut continuation token'() {
        given:
        List<String> keys = [
                'animals/cat.txt',
                'animals/dog.txt',
                'animals/mammals/fox.txt',
                'plants/oak.txt',
                'plants/pine.txt',
        ]
        keys.each { awsS3Bucket.upload(UploadRequest.fromBytes(TEXT.bytes, it, CONTENT_TYPE)) }
        ListObjectsRequest request = new ListObjectsRequest(2)

        when:
        def firstPage = awsS3Bucket.listObjects(request)
        def replayedFirstPage = awsS3Bucket.listObjects(request)
        def secondPage = awsS3Bucket.listObjects(new ListObjectsRequest(2, null, firstPage.continuationToken.orElse(null)))

        then:
        firstPage.keys == ['animals/cat.txt', 'animals/dog.txt']
        replayedFirstPage.keys == firstPage.keys
        replayedFirstPage.continuationToken == firstPage.continuationToken
        secondPage.keys == ['animals/mammals/fox.txt', 'plants/oak.txt']

        cleanup:
        keys.each {
            awsS3Bucket.delete(it)
        }
    }

    void 'AWS continuation token is scoped to the original request'() {
        given:
        List<String> keys = [
                'animals/cat.txt',
                'animals/dog.txt',
                'animals/mammals/fox.txt',
                'plants/oak.txt',
                'plants/pine.txt',
        ]
        keys.each { awsS3Bucket.upload(UploadRequest.fromBytes(TEXT.bytes, it, CONTENT_TYPE)) }
        def firstPage = awsS3Bucket.listObjects(new ListObjectsRequest(2, 'animals/'))

        when:
        awsS3Bucket.listObjects(new ListObjectsRequest(3, 'plants/', firstPage.continuationToken.orElse(null)))

        then:
        def e = thrown(Exception)
        e.message == 'AWS S3 continuation token does not match the current request'

        cleanup:
        keys.each {
            awsS3Bucket.delete(it)
        }
    }
}
