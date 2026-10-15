package io.micronaut.objectstorage.db

import io.micronaut.objectstorage.MultipartObjectStorageOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

import java.security.MessageDigest

@MicronautTest
class DbMultipartOperationsSingleColumnSpec extends MultipartObjectStorageOperationsSpecification implements TestPropertyProvider {

    @Inject
    DbStorageOperations operations

    @Inject
    DbBucketOperations bucketOperations

    @Inject
    DbMultipartOperations multipartOperations

    void 'parts are assembled in manifest order, replacing an existing object, with an S3-style etag'() {
        given:
        String key = 'multipart/replace.txt'
        operations.upload(UploadRequest.fromBytes('old'.bytes, key))
        def upload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest(key, 'text/plain', [a: 'b'])).upload
        def p1 = multipartOperations.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes('hello '.bytes, key)))
        // re-uploading a part replaces it
        multipartOperations.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes('xxxxxxxx'.bytes, key)))
        def p2 = multipartOperations.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes('multipart '.bytes, key)))
        def p3 = multipartOperations.uploadPart(new UploadPartRequest(upload, 3, UploadRequest.fromBytes('world'.bytes, key)))

        when:
        def response = multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [p1.part, p2.part, p3.part]))
        def entry = operations.retrieve(key).get()

        then:
        entry.inputStream.text == 'hello multipart world'
        entry.metadata == [a: 'b']
        entry.contentType.get() == 'text/plain'
        entry.nativeEntry.contentLength() == 21
        response.ETag == expectedEtag(['hello ', 'multipart ', 'world']) + '-3'
        operations.retrieve(key).get().nativeEntry.etag() == response.ETag

        cleanup:
        operations.delete(key)
    }

    private static String expectedEtag(List<String> parts) {
        MessageDigest md5 = MessageDigest.getInstance('MD5')
        parts.each { md5.update(MessageDigest.getInstance('MD5').digest(it.bytes)) }
        md5.digest().encodeHex().toString()
    }

    @Override
    MultipartObjectStorageOperations<?, ?, ?> getMultipartObjectStorage() {
        multipartOperations
    }

    @Override
    protected int nonFinalMultipartPartSizeBytes() {
        32
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        bucketOperations
    }

    @Override
    Map<String, String> getProperties() {
        DbTestProperties.h2('content-layout': 'SINGLE_COLUMN', 'chunk-size': '8')
    }
}
