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
        Path multipartDirectory = internalDirectory.resolve(LocalStorageOperations.MULTIPART_DIRECTORY)
        Path snapshotDirectory = internalDirectory.resolve(LocalStorageOperations.SNAPSHOT_DIRECTORY)
        Path legacyMetadataDirectory = bucketPath.resolve(LocalStorageOperations.LEGACY_METADATA_DIRECTORY)
        Files.createDirectories(multipartDirectory)
        Files.writeString(multipartDirectory.resolve('temporary'), 'hidden')
        Files.createDirectories(snapshotDirectory)
        Files.writeString(snapshotDirectory.resolve('temporary'), 'hidden')
        Files.createDirectories(legacyMetadataDirectory)
        Files.writeString(legacyMetadataDirectory.resolve('visible.txt'), 'owner=hidden')

        expect:
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/metadata')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/multipart')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.mn-storage/snapshots')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.metadata')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.metadata/')).keys.empty
        operations.listObjects(new ListObjectsRequest(5, '.metadata/visible.txt')).keys.empty
        operations.listObjects() == ['visible.txt'] as Set

        cleanup:
        Files.deleteIfExists(legacyMetadataDirectory.resolve('visible.txt'))
        Files.deleteIfExists(legacyMetadataDirectory)
        Files.deleteIfExists(multipartDirectory.resolve('temporary'))
        Files.deleteIfExists(multipartDirectory)
        Files.deleteIfExists(snapshotDirectory.resolve('temporary'))
        Files.deleteIfExists(snapshotDirectory)
    }

    void 'configured bucket path cannot use reserved local storage namespaces'() {
        given:
        Path root = Files.createTempDirectory('LocalStorageReservedMetadataSpec')
        Path bucketPath = root.resolve(name)
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('default')
        configuration.path = bucketPath

        when:
        new LocalStorageLayout(configuration)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Local storage bucket path uses the reserved ${reservedNamespace} namespace: ${bucketPath}"

        cleanup:
        Files.deleteIfExists(root)

        where:
        name          | reservedNamespace
        '.mn-storage' | '.mn-storage'
        '.Mn-Storage' | '.mn-storage'
        '.metadata'   | '.metadata'
        '.Metadata'   | '.metadata'
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
        ['.mn-storage', '.Mn-Storage', '.MN-STORAGE', '.metadata', '.Metadata', '.METADATA']
    }

    private static String reservedNamespace(String key) {
        def normalizedPath = Path.of(key).normalize()
        def firstSegment = normalizedPath.nameCount > 0 ? normalizedPath.getName(0).toString() : key
        firstSegment.equalsIgnoreCase(LocalStorageOperations.LEGACY_METADATA_DIRECTORY)
                ? LocalStorageOperations.LEGACY_METADATA_DIRECTORY
                : LocalStorageOperations.INTERNAL_DIRECTORY
    }
}
