package io.micronaut.objectstorage.aws

import io.micronaut.http.HttpStatus
import io.micronaut.objectstorage.tus.TusController
import io.micronaut.objectstorage.tus.TusHeaders
import io.micronaut.objectstorage.tus.TusMetadataCodec
import io.micronaut.objectstorage.tus.TusModuleConfiguration
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Arrays

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

@MicronautTest(startApplication = false)
class AwsS3TusUploadBackendLocalstackSpec extends Specification implements TestPropertyProvider {

    private static final String OBJECT_STORAGE_NAME = 'default'
    private static final String BUCKET_NAME = "tus-${System.currentTimeMillis()}"

    @Shared
    @AutoCleanup
    LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
        .withServices('s3')

    @Shared
    File workingDirectory = Files.createTempDirectory('micronaut-object-storage-tus-aws-spec').toFile()

    @Inject
    TusController controller

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AwsS3Operations operations

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AwsS3BucketOperations bucketOperations

    void setup() {
        bucketOperations.create(BUCKET_NAME)
    }

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
        if (bucketOperations.exists(BUCKET_NAME)) {
            bucketOperations.delete(BUCKET_NAME)
        }
    }

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        [
            (AwsS3Configuration.PREFIX + '.' + OBJECT_STORAGE_NAME + '.bucket') : BUCKET_NAME,
            (TusModuleConfiguration.PREFIX + '.enabled')                        : 'true',
            (TusModuleConfiguration.PREFIX + '.storage-directory')              : workingDirectory.absolutePath,
            'aws.accessKeyId'                                                   : localstack.accessKey,
            'aws.secretKey'                                                     : localstack.secretKey,
            'aws.region'                                                        : localstack.region,
            'aws.services.s3.endpoint-override'                                 : localstack.getEndpoint().toString()
        ]
    }

    void 'it buffers small tus chunks into AWS multipart uploads and finalizes the object'() {
        given:
        byte[] first = repeated((byte) 'a', 3 * 1024 * 1024)
        byte[] second = repeated((byte) 'b', 2 * 1024 * 1024)
        byte[] third = repeated((byte) 'c', 2 * 1024 * 1024)
        byte[] expected = joined(first, second, third)

        String location = controller.create(OBJECT_STORAGE_NAME, expected.length, TusMetadataCodec.encode([
            key        : 'videos/demo.bin',
            contentType: 'application/octet-stream',
            owner      : 'micronaut'
        ])).header('Location')
        String uploadId = location.tokenize('/').last()

        when:
        def firstPatch = controller.patch(OBJECT_STORAGE_NAME, uploadId, 0L, first)
        def secondPatch = controller.patch(OBJECT_STORAGE_NAME, uploadId, first.length, second)
        def headResponse = controller.head(OBJECT_STORAGE_NAME, uploadId)
        def thirdPatch = controller.patch(OBJECT_STORAGE_NAME, uploadId, first.length + second.length, third)
        def stored = operations.retrieve('videos/demo.bin').orElseThrow()

        then:
        firstPatch.status() == HttpStatus.NO_CONTENT
        firstPatch.header(TusHeaders.OFFSET) == Integer.toString(first.length)
        secondPatch.status() == HttpStatus.NO_CONTENT
        secondPatch.header(TusHeaders.OFFSET) == Integer.toString(first.length + second.length)
        headResponse.status() == HttpStatus.NO_CONTENT
        headResponse.header(TusHeaders.OFFSET) == Integer.toString(first.length + second.length)
        thirdPatch.status() == HttpStatus.NO_CONTENT
        thirdPatch.header(TusHeaders.OFFSET) == Integer.toString(expected.length)
        stored.inputStream.bytes == expected
        stored.metadata == [owner: 'micronaut']
    }

    void 'it aborts an in-progress AWS tus upload without creating an object'() {
        given:
        String uploadId = controller.create(OBJECT_STORAGE_NAME, 4, TusMetadataCodec.encode([
            key: 'videos/abort.bin'
        ])).header('Location').tokenize('/').last()
        controller.patch(OBJECT_STORAGE_NAME, uploadId, 0L, 'ab'.bytes)

        when:
        def deleteResponse = controller.delete(OBJECT_STORAGE_NAME, uploadId)
        def headResponse = controller.head(OBJECT_STORAGE_NAME, uploadId)

        then:
        deleteResponse.status() == HttpStatus.NO_CONTENT
        headResponse.status() == HttpStatus.GONE
        !operations.retrieve('videos/abort.bin').present
    }

    private static byte[] repeated(byte value, int length) {
        byte[] bytes = new byte[length]
        Arrays.fill(bytes, value)
        bytes
    }

    private static byte[] joined(byte[]... chunks) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        chunks.each(outputStream::write)
        outputStream.toByteArray()
    }
}
