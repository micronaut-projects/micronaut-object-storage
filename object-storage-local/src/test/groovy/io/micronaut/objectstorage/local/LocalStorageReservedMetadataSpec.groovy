package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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

    void 'reserved local storage keys are rejected for upload without corrupting stored metadata'() {
        given:
        def request = UploadRequest.fromBytes('safe'.bytes, 'foo.txt', 'text/plain')
        request.metadata = [owner: 'safe']
        operations.upload(request)

        when:
        operations.upload(UploadRequest.fromBytes('attacker'.bytes, key, 'text/plain'))

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Key uses the reserved ${reservedNamespace(key)} namespace: ${key}"
        operations.listObjects() == ['foo.txt'] as Set
        operations.retrieve('foo.txt').get().metadata == [owner: 'safe']

        where:
        key << blockedKeys()
    }

    void 'reserved local storage keys are rejected for direct object operations'() {
        when:
        operations.retrieve(key)

        then:
        def retrieveException = thrown(IllegalArgumentException)
        retrieveException.message == "Key uses the reserved ${reservedNamespace(key)} namespace: ${key}"

        when:
        operations.delete(key)

        then:
        def deleteException = thrown(IllegalArgumentException)
        deleteException.message == "Key uses the reserved ${reservedNamespace(key)} namespace: ${key}"

        when:
        operations.exists(key)

        then:
        def existsException = thrown(IllegalArgumentException)
        existsException.message == "Key uses the reserved ${reservedNamespace(key)} namespace: ${key}"

        when:
        operations.upload(UploadRequest.fromBytes('blocked'.bytes, key, 'text/plain'))

        then:
        def uploadException = thrown(IllegalArgumentException)
        uploadException.message == "Key uses the reserved ${reservedNamespace(key)} namespace: ${key}"

        where:
        key << blockedKeys()
    }

    void 'listing never exposes reserved local storage namespaces'() {
        given:
        operations.upload(UploadRequest.fromBytes('visible'.bytes, 'visible.txt', 'text/plain'))
        def bucketPath = operations.retrieve('visible.txt').get().nativeEntry.parent
        Path internalDirectory = bucketPath.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
        Path snapshotDirectory = internalDirectory.resolve(LocalStorageOperations.SNAPSHOT_DIRECTORY)
        Files.createDirectories(snapshotDirectory)
        Files.writeString(snapshotDirectory.resolve('temporary'), 'hidden')

        expect:
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/metadata')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/snapshots')).keys.empty
        operations.listObjects() == ['visible.txt'] as Set

        cleanup:
        Files.deleteIfExists(snapshotDirectory.resolve('temporary'))
        Files.deleteIfExists(snapshotDirectory)
    }

    private static List<String> blockedKeys() {
        def keys = reservedNamespaces()
                .collectMany { reservedNamespace ->
                    [
                            reservedNamespace,
                            "${reservedNamespace}/nested.txt",
                            "foo/../${reservedNamespace}/nested.txt",
                            "./${reservedNamespace}/nested.txt"
                    ]
                }
        if (File.separatorChar != '/') {
            reservedNamespaces().each { reservedNamespace ->
                keys << "${reservedNamespace}${File.separator}nested.txt"
                keys << "foo${File.separator}..${File.separator}${reservedNamespace}${File.separator}nested.txt"
                keys << ".${File.separator}${reservedNamespace}${File.separator}nested.txt"
            }
        }
        keys
    }

    private static List<String> reservedNamespaces() {
        ['.mn-storage', '.Mn-Storage', '.MN-STORAGE']
    }

    private static String reservedNamespace(String key) {
        '.mn-storage'
    }
}
