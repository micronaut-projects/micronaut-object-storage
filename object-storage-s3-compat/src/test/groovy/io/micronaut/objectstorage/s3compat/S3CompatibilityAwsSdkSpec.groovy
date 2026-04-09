package io.micronaut.objectstorage.s3compat

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class S3CompatibilityAwsSdkSpec extends Specification {

    void 'aws sdk can use sigv4 against the s3 compatibility endpoint'() {
        given:
        Path storagePath = Files.createTempDirectory('mn-object-storage-s3compat')
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.object-storage.local.default.enabled'                  : 'true',
            'micronaut.object-storage.local.default.path'                     : storagePath.toString(),
            'micronaut.object-storage.s3-compat.enabled'                      : 'true',
            'micronaut.object-storage.s3-compat.auth-mode'                    : 'sigv4',
            'micronaut.object-storage.s3-compat.access-key-id'                : 'test-access-key',
            'micronaut.object-storage.s3-compat.secret-access-key'            : 'test-secret-key',
            'micronaut.object-storage.s3-compat.region'                       : 'us-east-1',
            'micronaut.object-storage.s3-compat.assets.enabled'               : 'true',
            'micronaut.object-storage.s3-compat.assets.storage'               : 'default',
            'micronaut.object-storage.s3-compat.assets.base-path'             : 'exports/public',
        ])
        S3Client client = s3Client(server.URI, 'test-access-key', 'test-secret-key')

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

        then:
        head.contentLength() == 11L
        head.contentType() == 'text/plain'
        object.asUtf8String() == 'hello world'
        listing.contents()*.key() == ['docs/hello.txt']

        when:
        client.deleteObject { it.bucket('assets').key('docs/hello.txt') }
        def emptyListing = client.listObjectsV2(ListObjectsV2Request.builder().bucket('assets').prefix('docs/').build())

        then:
        emptyListing.contents().isEmpty()

        cleanup:
        client?.close()
        server?.close()
        storagePath?.toFile()?.deleteDir()
    }

    void 'it rejects invalid sigv4 credentials'() {
        given:
        Path storagePath = Files.createTempDirectory('mn-object-storage-s3compat-invalid')
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.object-storage.local.default.enabled'                  : 'true',
            'micronaut.object-storage.local.default.path'                     : storagePath.toString(),
            'micronaut.object-storage.s3-compat.enabled'                      : 'true',
            'micronaut.object-storage.s3-compat.auth-mode'                    : 'sigv4',
            'micronaut.object-storage.s3-compat.access-key-id'                : 'test-access-key',
            'micronaut.object-storage.s3-compat.secret-access-key'            : 'test-secret-key',
            'micronaut.object-storage.s3-compat.region'                       : 'us-east-1',
            'micronaut.object-storage.s3-compat.assets.enabled'               : 'true',
            'micronaut.object-storage.s3-compat.assets.storage'               : 'default',
        ])
        S3Client client = s3Client(server.URI, 'test-access-key', 'wrong-secret')

        when:
        client.listObjectsV2(ListObjectsV2Request.builder().bucket('assets').build())

        then:
        def e = thrown(S3Exception)
        e.statusCode() == 403
        e.awsErrorDetails().errorCode() == 'SignatureDoesNotMatch'

        cleanup:
        client?.close()
        server?.close()
        storagePath?.toFile()?.deleteDir()
    }

    private static S3Client s3Client(URI endpoint, String accessKeyId, String secretAccessKey) {
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of('us-east-1'))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .checksumValidationEnabled(false)
                .build())
            .build()
    }
}
