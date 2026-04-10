package io.micronaut.objectstorage.s3compat

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.CompletedPart
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.net.URI

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

class S3CompatibilityAwsBackendSpec extends Specification {

    @Shared
    @AutoCleanup
    LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
        .withServices("s3")

    void 'aws sdk can use sigv4 against the s3 compatibility endpoint backed by aws storage'() {
        given:
        localstack.start()
        String backingBucket = "mn-s3compat-${System.currentTimeMillis()}"
        S3Client backendClient = localstackClient()
        backendClient.createBucket { it.bucket(backingBucket) }
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.object-storage.aws.default.enabled'                  : 'true',
            'micronaut.object-storage.aws.default.bucket'                   : backingBucket,
            'aws.accessKeyId'                                               : localstack.accessKey,
            'aws.secretKey'                                                 : localstack.secretKey,
            'aws.region'                                                    : localstack.region,
            'aws.services.s3.endpoint-override'                             : localstack.getEndpoint().toString(),
            'micronaut.object-storage.s3-compat.enabled'                    : 'true',
            'micronaut.object-storage.s3-compat.auth-mode'                  : 'sigv4',
            'micronaut.object-storage.s3-compat.access-key-id'              : 'test-access-key',
            'micronaut.object-storage.s3-compat.secret-access-key'          : 'test-secret-key',
            'micronaut.object-storage.s3-compat.region'                     : localstack.region,
            'micronaut.object-storage.s3-compat.assets.enabled'             : 'true',
            'micronaut.object-storage.s3-compat.assets.storage'             : 'default',
            'micronaut.object-storage.s3-compat.assets.storage-provider'    : 'aws',
            'micronaut.object-storage.s3-compat.assets.base-path'           : 'exports/public',
        ])
        S3Client client = s3Client(server.URI, 'test-access-key', 'test-secret-key', localstack.region)

        when:
        client.putObject(
            PutObjectRequest.builder()
                .bucket('assets')
                .key('docs/hello.txt')
                .contentType('text/plain')
                .build(),
            RequestBody.fromString('hello world')
        )
        def head = client.headObject(HeadObjectRequest.builder().bucket('assets').key('docs/hello.txt').build())
        ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(GetObjectRequest.builder().bucket('assets').key('docs/hello.txt').build())
        def listing = client.listObjectsV2(ListObjectsV2Request.builder().bucket('assets').prefix('docs/').build())
        def backingListing = backendClient.listObjectsV2(ListObjectsV2Request.builder().bucket(backingBucket).prefix('exports/public/docs/').build())

        then:
        head.contentLength() == 11L
        head.contentType() == 'text/plain'
        object.asUtf8String() == 'hello world'
        listing.contents()*.key() == ['docs/hello.txt']
        backingListing.contents()*.key() == ['exports/public/docs/hello.txt']

        when:
        client.deleteObject { it.bucket('assets').key('docs/hello.txt') }
        def emptyListing = client.listObjectsV2(ListObjectsV2Request.builder().bucket('assets').prefix('docs/').build())

        then:
        emptyListing.contents().isEmpty()

        cleanup:
        client?.close()
        server?.close()
        backendClient?.close()
    }

    void 'aws-backed buckets support multipart upload routes through the compatibility endpoint'() {
        given:
        localstack.start()
        String backingBucket = "mn-s3compat-multipart-${System.currentTimeMillis()}"
        S3Client backendClient = localstackClient()
        backendClient.createBucket { it.bucket(backingBucket) }
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.object-storage.aws.default.enabled'                  : 'true',
            'micronaut.object-storage.aws.default.bucket'                   : backingBucket,
            'aws.accessKeyId'                                               : localstack.accessKey,
            'aws.secretKey'                                                 : localstack.secretKey,
            'aws.region'                                                    : localstack.region,
            'aws.services.s3.endpoint-override'                             : localstack.getEndpoint().toString(),
            'micronaut.object-storage.s3-compat.enabled'                    : 'true',
            'micronaut.object-storage.s3-compat.auth-mode'                  : 'sigv4',
            'micronaut.object-storage.s3-compat.access-key-id'              : 'test-access-key',
            'micronaut.object-storage.s3-compat.secret-access-key'          : 'test-secret-key',
            'micronaut.object-storage.s3-compat.region'                     : localstack.region,
            'micronaut.object-storage.s3-compat.assets.enabled'             : 'true',
            'micronaut.object-storage.s3-compat.assets.storage'             : 'default',
            'micronaut.object-storage.s3-compat.assets.storage-provider'    : 'aws',
            'micronaut.object-storage.s3-compat.assets.base-path'           : 'exports/public',
        ])
        S3Client client = s3Client(server.URI, 'test-access-key', 'test-secret-key', localstack.region)
        byte[] partBytes = 'hello multipart world'.bytes

        when:
        def initiated = client.createMultipartUpload {
            it.bucket('assets')
                .key('docs/multipart.txt')
                .contentType('text/plain')
        }
        def part = client.uploadPart(
            {
                it.bucket('assets')
                    .key('docs/multipart.txt')
                    .uploadId(initiated.uploadId())
                    .partNumber(1)
            },
            RequestBody.fromBytes(partBytes)
        )
        def listedParts = client.listParts {
            it.bucket('assets')
                .key('docs/multipart.txt')
                .uploadId(initiated.uploadId())
        }
        client.completeMultipartUpload {
            it.bucket('assets')
                .key('docs/multipart.txt')
                .uploadId(initiated.uploadId())
                .multipartUpload { upload ->
                    upload.parts(
                        CompletedPart.builder()
                            .partNumber(1)
                            .eTag(part.eTag())
                            .build()
                    )
                }
        }
        ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(GetObjectRequest.builder().bucket('assets').key('docs/multipart.txt').build())
        ResponseBytes<GetObjectResponse> backendObject = backendClient.getObjectAsBytes(GetObjectRequest.builder().bucket(backingBucket).key('exports/public/docs/multipart.txt').build())

        then:
        listedParts.parts().size() == 1
        listedParts.parts().first().partNumber() == 1
        listedParts.parts().first().size() == partBytes.length
        object.asByteArray() == partBytes
        backendObject.asByteArray() == partBytes

        when:
        def aborted = client.createMultipartUpload {
            it.bucket('assets')
                .key('docs/aborted.txt')
        }
        client.uploadPart(
            {
                it.bucket('assets')
                    .key('docs/aborted.txt')
                    .uploadId(aborted.uploadId())
                    .partNumber(1)
            },
            RequestBody.fromBytes('bye'.bytes)
        )
        client.abortMultipartUpload {
            it.bucket('assets')
                .key('docs/aborted.txt')
                .uploadId(aborted.uploadId())
        }
        client.listParts {
            it.bucket('assets')
                .key('docs/aborted.txt')
                .uploadId(aborted.uploadId())
        }

        then:
        def e = thrown(S3Exception)
        e.statusCode() == 404
        e.awsErrorDetails().errorCode() == 'NoSuchUpload'

        cleanup:
        client?.close()
        server?.close()
        backendClient?.close()
    }

    private S3Client localstackClient() {
        s3Client(
            localstack.getEndpoint(),
            localstack.accessKey,
            localstack.secretKey,
            localstack.region
        )
    }

    private static S3Client s3Client(URI endpoint, String accessKeyId, String secretAccessKey, String region) {
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .checksumValidationEnabled(false)
                .build())
            .build()
    }
}
