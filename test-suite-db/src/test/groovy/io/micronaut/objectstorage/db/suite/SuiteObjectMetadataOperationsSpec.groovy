package io.micronaut.objectstorage.db.suite

import io.micronaut.objectstorage.db.*

import io.micronaut.objectstorage.ObjectMetadataOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class SuiteObjectMetadataOperationsSpec extends ObjectMetadataOperationsSpecification implements TestPropertyProvider {

    @Inject
    DbStorageOperations operations

    @Inject
    DbObjectMetadataOperations metadataOperations

    void 'metadata reports the stored content length, etag and modification time'() {
        given:
        def response = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'info.txt', 'text/plain'))

        when:
        def entry = metadataOperations.retrieve('info.txt').get()

        then:
        entry.contentLength() == 5
        entry.etag() == response.ETag
        entry.etag() == '5d41402abc4b2a76b9719d911017c592'
        entry.lastModified()
        entry.contentType() == 'text/plain'
        entry.nativeEntry().key() == 'info.txt'

        cleanup:
        operations.delete('info.txt')
    }

    void 'saving metadata of a missing object fails'() {
        when:
        metadataOperations.save(new ObjectMetadataWrite('missing.txt', [a: 'b']))

        then:
        thrown(ObjectStorageException)
    }

    void 'metadata round-trips special characters'() {
        given:
        Map<String, String> metadata = [quote: 'say "hi"', slash: 'a\\b', unicode: 'ñ€😀', newline: 'a\nb', empty: '']
        UploadRequest request = UploadRequest.fromBytes('x'.bytes, 'special.txt')
        request.metadata = metadata
        operations.upload(request)

        expect:
        operations.retrieve('special.txt').get().metadata == metadata

        cleanup:
        operations.delete('special.txt')
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        operations
    }

    @Override
    ObjectMetadataOperations<?> getObjectMetadataOperations() {
        metadataOperations
    }

    @Override
    Map<String, String> getProperties() {
        SuiteProperties.storage('objectmetadata')
    }
}
