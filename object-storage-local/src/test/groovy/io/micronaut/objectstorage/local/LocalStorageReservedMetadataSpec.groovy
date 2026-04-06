package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

import java.io.File

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
        ex.message == 'Key uses the reserved .metadata namespace: .metadata/foo.txt'
        operations.listObjects() == ['foo.txt'] as Set
        operations.retrieve('foo.txt').get().metadata == [owner: 'safe']
    }

    void 'reserved metadata keys are rejected for direct object operations'() {
        when:
        operations.retrieve(key)

        then:
        def retrieveException = thrown(IllegalArgumentException)
        retrieveException.message == "Key uses the reserved .metadata namespace: ${key}"

        when:
        operations.delete(key)

        then:
        def deleteException = thrown(IllegalArgumentException)
        deleteException.message == "Key uses the reserved .metadata namespace: ${key}"

        when:
        operations.exists(key)

        then:
        def existsException = thrown(IllegalArgumentException)
        existsException.message == "Key uses the reserved .metadata namespace: ${key}"

        when:
        operations.upload(UploadRequest.fromBytes('blocked'.bytes, key, 'text/plain'))

        then:
        def uploadException = thrown(IllegalArgumentException)
        uploadException.message == "Key uses the reserved .metadata namespace: ${key}"

        where:
        key << reservedKeys()
    }

    void 'listing never exposes the reserved metadata namespace'() {
        given:
        operations.upload(UploadRequest.fromBytes('visible'.bytes, 'visible.txt', 'text/plain'))

        expect:
        operations.listObjects(new ListObjectsRequest(5, '.metadata')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.metadata/')).keys.empty
        operations.listObjects() == ['visible.txt'] as Set
    }

    private static List<String> reservedKeys() {
        def keys = ['.metadata', '.metadata/nested.txt']
        if (File.separatorChar != '/') {
            keys << ".metadata${File.separator}nested.txt"
        }
        keys
    }
}
