package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.MultipartObjectStorageOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.objectstorage.multipart.MultipartPart
import io.micronaut.objectstorage.multipart.MultipartUploadHandle
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

@MicronautTest
class LocalStorageMultipartOperationsSpec extends MultipartObjectStorageOperationsSpecification implements TestPropertyProvider {

    private static final int LOCAL_NON_FINAL_MULTIPART_PART_SIZE_BYTES = 32

    @Inject
    LocalStorageOperations operations

    @Inject
    LocalStorageBucketOperations bucketOperations

    @Inject
    LocalStorageMultipartOperations multipartOperations

    @Inject
    LocalStorageConfiguration configuration

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        bucketOperations
    }

    @Override
    MultipartObjectStorageOperations<?, ?, ?> getMultipartObjectStorage() {
        multipartOperations
    }

    @Override
    protected int nonFinalMultipartPartSizeBytes() {
        LOCAL_NON_FINAL_MULTIPART_PART_SIZE_BYTES
    }

    @Override
    Map<String, String> getProperties() {
        [(LocalStorageConfiguration.PREFIX + '.default.enabled'): 'true']
    }

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
    }

    void 'local multipart lists no parts for a new upload'() {
        given:
        String key = 'multipart-local/new-upload.txt'
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload

        when:
        def response = multipartOperations.listParts(new ListMultipartPartsRequest(upload, 10, ''))

        then:
        response.parts.empty
        !response.getContinuationToken().present

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
    }

    void 'local multipart scratch state is hidden until completion and cleaned up after completion'() {
        given:
        String key = 'multipart-local/demo.txt'
        Map<String, String> metadata = [owner: 'local']
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE, metadata))
        def upload = createResponse.upload

        when:
        def firstPart = multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('micro'.bytes, key, CONTENT_TYPE)))
        def secondPart = multipartOperations.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes('naut'.bytes, key, CONTENT_TYPE)))

        then:
        Files.exists(createResponse.nativeResponse.path)
        operations.listObjects().empty

        when:
        def response = multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [firstPart.part, secondPart.part]))
        def entry = operations.retrieve(key).get()

        then:
        response.ETag
        operations.listObjects() == [key] as Set
        entry.inputStream.text == 'micronaut'
        entry.metadata == metadata
        entry.contentType.get() == CONTENT_TYPE
        !Files.exists(createResponse.nativeResponse.path)
    }

    void 'failed local multipart part replacement preserves the previously uploaded part'() {
        given:
        String key = 'multipart-local/replacement.txt'
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload
        def originalPart = multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('original'.bytes, key, CONTENT_TYPE)))

        when:
        multipartOperations.uploadPart(new UploadPartRequest(upload, 1, new FailingUploadRequest(key, CONTENT_TYPE, 'replacement'.bytes, 4)))

        then:
        thrown(ObjectStorageException)

        when:
        def listedParts = multipartOperations.listParts(new ListMultipartPartsRequest(upload, 10)).parts
        def response = multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [originalPart.part]))
        def entry = operations.retrieve(key).get()

        then:
        listedParts*.partNumber == [1]
        listedParts*.ETag == [originalPart.part.ETag]
        response.ETag
        entry.inputStream.text == 'original'
    }

    void 'successful local multipart part replacement commits the latest part'() {
        given:
        String key = 'multipart-local/successful-replacement.txt'
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload
        multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('old'.bytes, key, CONTENT_TYPE)))
        String stalePartFileName = loadProperties(partPropertiesPath(createResponse.nativeResponse.path, 1)).getProperty('file')

        when:
        def replacementPart = multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('new'.bytes, key, CONTENT_TYPE)))
        def response = multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [replacementPart.part]))
        def entry = operations.retrieve(key).get()

        then:
        response.ETag
        entry.inputStream.text == 'new'
        !Files.exists(partDataPath(createResponse.nativeResponse.path, stalePartFileName))
    }

    void 'failed local multipart complete removes assembled temporary file and leaves upload abortable'() {
        given:
        String key = 'multipart-local/failed-complete.txt'
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload
        def uploadedPart = multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('micro'.bytes, key, CONTENT_TYPE)))
        MultipartPart missingPart = new MultipartPart(2, 'missing-etag', 4)

        when:
        multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [uploadedPart.part, missingPart]))

        then:
        thrown(ObjectStorageException)
        !operations.exists(key)
        Files.exists(createResponse.nativeResponse.path)
        !containsCompletedMultipartFile(createResponse.nativeResponse.path)

        when:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))

        then:
        !Files.exists(createResponse.nativeResponse.path)
    }

    void 'failed local multipart final upload removes assembled temporary file and leaves upload abortable'() {
        given:
        String key = 'multipart-local/failed-final-upload.txt'
        ObjectMetadataOperations<Path> metadataOperations = Mock()
        LocalStorageOperations failingOperations = new LocalStorageOperations(configuration, metadataOperations) {
            @Override
            UploadResponse<LocalStorageFile> upload(UploadRequest request) {
                throw new ObjectStorageException('final upload failed')
            }
        }
        LocalStorageMultipartOperations failingMultipartOperations = new LocalStorageMultipartOperations(configuration, failingOperations)
        def createResponse = failingMultipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload
        def firstPart = failingMultipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('micro'.bytes, key, CONTENT_TYPE)))
        def secondPart = failingMultipartOperations.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes('naut'.bytes, key, CONTENT_TYPE)))

        when:
        failingMultipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [firstPart.part, secondPart.part]))

        then:
        thrown(ObjectStorageException)
        !operations.exists(key)
        Files.exists(createResponse.nativeResponse.path)
        !containsCompletedMultipartFile(createResponse.nativeResponse.path)

        when:
        failingMultipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
        failingMultipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))

        then:
        !Files.exists(createResponse.nativeResponse.path)
    }

    void 'local multipart complete rejects ETag mismatches'() {
        given:
        def fixture = createUploadWithSinglePart('multipart-local/wrong-etag.txt')
        MultipartPart wrongPart = new MultipartPart(fixture.part.partNumber, 'wrong-etag', fixture.part.size)

        when:
        multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(fixture.upload, [wrongPart]))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Local multipart part ETag does not match: 1'
        !containsCompletedMultipartFile(fixture.uploadPath)

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(fixture.upload))
    }

    void 'local multipart tolerates empty stored content type'() {
        given:
        def fixture = createUploadWithSinglePart('multipart-local/empty-content-type.txt')
        Path uploadPropertiesPath = fixture.uploadPath.resolve('upload.properties')
        Properties properties = loadProperties(uploadPropertiesPath)
        properties.setProperty('contentType', '')
        storeProperties(uploadPropertiesPath, properties)

        when:
        def response = multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(fixture.upload, [fixture.part]))

        then:
        response.ETag
    }

    void 'local multipart rejects incomplete part metadata'() {
        given:
        def fixture = createUploadWithSinglePart("multipart-local/incomplete-${propertyName}.txt")
        Properties properties = loadProperties(partPropertiesPath(fixture.uploadPath, 1))
        properties.remove(propertyName)
        storeProperties(partPropertiesPath(fixture.uploadPath, 1), properties)

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(fixture.upload, 10))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Local multipart part metadata is incomplete: 1'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(fixture.upload))

        where:
        propertyName << ['eTag', 'size', 'file']
    }

    void 'local multipart rejects missing part data'() {
        given:
        def fixture = createUploadWithSinglePart('multipart-local/missing-part-data.txt')
        Properties properties = loadProperties(partPropertiesPath(fixture.uploadPath, 1))
        Files.delete(partDataPath(fixture.uploadPath, properties.getProperty('file')))

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(fixture.upload, 10))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Local multipart part does not exist: 1'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(fixture.upload))
    }

    void 'local multipart rejects part size metadata mismatches'() {
        given:
        def fixture = createUploadWithSinglePart("multipart-local/${sizeValue}.txt")
        Properties properties = loadProperties(partPropertiesPath(fixture.uploadPath, 1))
        properties.setProperty('size', sizeValue)
        storeProperties(partPropertiesPath(fixture.uploadPath, 1), properties)

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(fixture.upload, 10))

        then:
        ObjectStorageException e = thrown()
        e.message == expectedMessage

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(fixture.upload))

        where:
        sizeValue      | expectedMessage
        '999'          | 'Local multipart part size does not match metadata: 1'
        'not-a-number' | 'Local multipart part size is invalid: 1'
    }

    void 'local multipart rejects invalid stored part file names'() {
        given:
        def fixture = createUploadWithSinglePart("multipart-local/invalid-part-file-${index}.txt")
        Properties properties = loadProperties(partPropertiesPath(fixture.uploadPath, 1))
        properties.setProperty('file', fileName)
        storeProperties(partPropertiesPath(fixture.uploadPath, 1), properties)

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(fixture.upload, 10))

        then:
        ObjectStorageException e = thrown()
        e.message == "Invalid local multipart part file: ${fileName}"

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(fixture.upload))

        where:
        index | fileName
        1     | 'part.txt'
        2     | '../escape.part'
    }

    void 'local multipart rejects malformed part metadata file names'() {
        given:
        def fixture = createUploadWithSinglePart('multipart-local/malformed-part-metadata.txt')
        Files.writeString(fixture.uploadPath.resolve('parts').resolve('broken.properties'), '')

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(fixture.upload, 10))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Invalid local multipart part file: broken.properties'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(fixture.upload))
    }

    void 'local multipart abort validates the upload key before deleting an existing upload'() {
        given:
        String key = 'multipart-local/abort-key.txt'
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload
        def wrongKeyUpload = new MultipartUploadHandle('multipart-local/other.txt', upload.uploadId)

        when:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(wrongKeyUpload))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Multipart upload key does not match local upload session'
        Files.exists(createResponse.nativeResponse.path)

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
    }

    void 'local multipart continuation tokens are bound to the upload and page size'() {
        given:
        String firstKey = 'multipart-local/first.txt'
        String secondKey = 'multipart-local/second.txt'
        def firstUpload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(firstKey, CONTENT_TYPE)).upload
        def secondUpload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(secondKey, CONTENT_TYPE)).upload
        multipartOperations.uploadPart(new UploadPartRequest(firstUpload, 1, UploadRequest.fromBytes('one'.bytes, firstKey, CONTENT_TYPE)))
        multipartOperations.uploadPart(new UploadPartRequest(firstUpload, 2, UploadRequest.fromBytes('two'.bytes, firstKey, CONTENT_TYPE)))
        multipartOperations.uploadPart(new UploadPartRequest(secondUpload, 1, UploadRequest.fromBytes('one'.bytes, secondKey, CONTENT_TYPE)))
        multipartOperations.uploadPart(new UploadPartRequest(secondUpload, 2, UploadRequest.fromBytes('two'.bytes, secondKey, CONTENT_TYPE)))
        String token = multipartOperations.listParts(new ListMultipartPartsRequest(firstUpload, 1)).getContinuationToken().get()

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(secondUpload, 1, token))

        then:
        ObjectStorageException uploadException = thrown()
        uploadException.message == 'Local multipart continuation token does not match the current request'

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(firstUpload, 2, token))

        then:
        ObjectStorageException pageSizeException = thrown()
        pageSizeException.message == 'Local multipart continuation token does not match the current request'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(firstUpload))
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(secondUpload))
    }

    void 'local multipart rejects invalid continuation tokens before listing parts'() {
        given:
        String key = 'multipart-local/invalid-token.txt'
        def upload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(upload, 2, token))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Invalid local multipart continuation token'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))

        where:
        token << [
            'part-2',
            'local-multipart:v1:not-base64',
            'local-multipart:v1:' + Base64.getUrlEncoder().withoutPadding().encodeToString('too-short'.getBytes(StandardCharsets.UTF_8))
        ]
    }

    void 'local multipart rejects malformed continuation token page sizes'() {
        given:
        String key = 'multipart-local/malformed-page-size-token.txt'
        def upload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(
            upload,
            2,
            continuationToken(key, upload.uploadId, 'two', '1')
        ))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Invalid local multipart continuation token'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))
    }

    void 'local multipart rejects non-positive continuation markers before listing parts'() {
        given:
        String key = 'multipart-local/non-positive-token-marker.txt'
        def upload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE)).upload

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(
            upload,
            2,
            continuationToken(key, upload.uploadId, 2, marker)
        ))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Invalid local multipart continuation token'

        cleanup:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(upload))

        where:
        marker << [0, -1]
    }

    void 'local multipart rejects reserved keys before creating scratch state'() {
        given:
        Path multipartBucketDirectory = new LocalStorageLayout(configuration).multipartBucketDirectory()

        when:
        multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest('.mn-storage/multipart.txt', CONTENT_TYPE))

        then:
        thrown(IllegalArgumentException)
        !Files.exists(multipartBucketDirectory)
    }

    void 'local multipart rejects invalid upload ids'() {
        when:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(new MultipartUploadHandle('multipart-local/object.txt', 'not-a-uuid')))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Invalid local multipart upload id: not-a-uuid'
    }

    private MultipartUploadFixture createUploadWithSinglePart(String key) {
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, CONTENT_TYPE))
        def upload = createResponse.upload
        def part = multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('part'.bytes, key, CONTENT_TYPE))).part
        new MultipartUploadFixture(upload, createResponse.nativeResponse.path, part)
    }

    private static Path partPropertiesPath(Path uploadPath, int partNumber) {
        uploadPath.resolve('parts').resolve(partNumber + '.properties')
    }

    private static Path partDataPath(Path uploadPath, String fileName) {
        uploadPath.resolve('parts').resolve(fileName)
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties()
        Files.newInputStream(path).withCloseable { properties.load(it) }
        properties
    }

    private static void storeProperties(Path path, Properties properties) {
        Files.newOutputStream(path).withCloseable { properties.store(it, 'test') }
    }

    private static final class MultipartUploadFixture {
        private final MultipartUploadHandle upload
        private final Path uploadPath
        private final MultipartPart part

        private MultipartUploadFixture(MultipartUploadHandle upload, Path uploadPath, MultipartPart part) {
            this.upload = upload
            this.uploadPath = uploadPath
            this.part = part
        }
    }

    private static boolean containsCompletedMultipartFile(Path uploadPath) {
        Files.list(uploadPath).withCloseable { stream ->
            stream.anyMatch { path -> path.fileName.toString().endsWith('.complete') }
        }
    }

    private static String continuationToken(String key, String uploadId, int pageSize, int marker) {
        continuationToken(key, uploadId, Integer.toString(pageSize), Integer.toString(marker))
    }

    private static String continuationToken(String key, String uploadId, String pageSize, String marker) {
        String payload = new StringJoiner('\n')
            .add(encodeTokenPart(key))
            .add(encodeTokenPart(uploadId))
            .add(encodeTokenPart(pageSize))
            .add(encodeTokenPart(marker))
            .toString()
        'local-multipart:v1:' + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
    }

    private static String encodeTokenPart(String value) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }

    private static final class FailingUploadRequest implements UploadRequest {
        private final String key
        private final String contentType
        private final byte[] bytes
        private final int failureAfterBytes

        private FailingUploadRequest(String key, String contentType, byte[] bytes, int failureAfterBytes) {
            this.key = key
            this.contentType = contentType
            this.bytes = bytes
            this.failureAfterBytes = failureAfterBytes
        }

        @Override
        Optional<String> getContentType() {
            Optional.of(contentType)
        }

        @Override
        String getKey() {
            key
        }

        @Override
        Optional<Long> getContentSize() {
            Optional.of((long) bytes.length)
        }

        @Override
        InputStream getInputStream() {
            new FailingInputStream(bytes, failureAfterBytes)
        }

        @Override
        Map<String, String> getMetadata() {
            [:]
        }
    }

    private static final class FailingInputStream extends InputStream {
        private final byte[] bytes
        private final int failureAfterBytes
        private int index

        private FailingInputStream(byte[] bytes, int failureAfterBytes) {
            this.bytes = bytes
            this.failureAfterBytes = failureAfterBytes
        }

        @Override
        int read() throws IOException {
            if (index >= failureAfterBytes) {
                throw new IOException('simulated upload failure')
            }
            if (index >= bytes.length) {
                return -1
            }
            bytes[index++] & 0xff
        }

        @Override
        int read(byte[] buffer, int offset, int length) throws IOException {
            if (index >= failureAfterBytes) {
                throw new IOException('simulated upload failure')
            }
            if (index >= bytes.length) {
                return -1
            }
            int count = Math.min(length, Math.min(bytes.length, failureAfterBytes) - index)
            System.arraycopy(bytes, index, buffer, offset, count)
            index += count
            count
        }
    }
}
