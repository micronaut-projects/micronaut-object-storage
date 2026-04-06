package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class LocalStorageReservedMetadataSpec extends Specification implements TestPropertyProvider {

    @Inject
    LocalStorageOperations operations

    @Override
    Map<String, String> getProperties() {
        [(LocalStorageConfiguration.PREFIX + '.default.enabled'): 'true']
    }

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
    }

    void 'reserved metadata keys are rejected for upload without corrupting stored metadata'() {
        given:
        def request = UploadRequest.fromBytes('safe'.bytes, 'foo.txt', 'text/plain')
        request.metadata = [owner: 'safe']
        operations.upload(request)

        when:
        operations.upload(UploadRequest.fromBytes('attacker'.bytes, '.metadata/foo.txt', 'text/plain'))

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('.metadata')
        operations.listObjects() == ['foo.txt'] as Set
        operations.retrieve('foo.txt').get().metadata == [owner: 'safe']
    }

    void 'reserved metadata keys are rejected for direct object operations'() {
        when:
        operations.retrieve(key)

        then:
        thrown(IllegalArgumentException)

        when:
        operations.delete(key)

        then:
        thrown(IllegalArgumentException)

        when:
        operations.exists(key)

        then:
        thrown(IllegalArgumentException)

        when:
        operations.upload(UploadRequest.fromBytes('blocked'.bytes, key, 'text/plain'))

        then:
        thrown(IllegalArgumentException)

        where:
        key << ['.metadata', '.metadata/nested.txt']
    }

    void 'listing never exposes the reserved metadata namespace'() {
        given:
        operations.upload(UploadRequest.fromBytes('visible'.bytes, 'visible.txt', 'text/plain'))

        expect:
        operations.listObjects(new ListObjectsRequest(5, '.metadata')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.metadata/')).keys.empty
        operations.listObjects() == ['visible.txt'] as Set
    }
}
