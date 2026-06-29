package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit

class LocalStorageMetadataCompatibilitySpec extends Specification {

    private Path rootDirectory
    private Path bucketPath
    private ApplicationContext ctx

    void setup() {
        rootDirectory = Files.createTempDirectory('LocalStorageMetadataCompatibilitySpec')
        bucketPath = rootDirectory.resolve('default')
        ctx = ApplicationContext.run([
            'micronaut.object-storage.local.default.path': bucketPath.toString()
        ])
    }

    void cleanup() {
        ctx?.close()
        if (rootDirectory != null && Files.exists(rootDirectory)) {
            LocalStorageBucketOperations.deleteRecursively(rootDirectory)
        }
    }

    void 'default metadata operations do not create sidecar directories on context startup'() {
        given:
        ctx.getBean(LocalStorageOperations)
        ctx.getBean(LocalStorageObjectMetadataOperations)
        ctx.getBean(LocalStorageBucketMetadataOperations)

        expect:
        !Files.exists(rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))
        !Files.exists(bucketPath.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))
        !Files.exists(bucketPath.resolve(LocalStorageOperations.LEGACY_METADATA_DIRECTORY))
    }

    void 'local upload persists deterministic content etag in sidecar metadata'() {
        given:
        def operations = ctx.getBean(LocalStorageOperations)
        def metadataOperations = ctx.getBean(LocalStorageObjectMetadataOperations)

        when:
        def firstResponse = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'first.txt', 'text/plain'))
        def secondResponse = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'second.txt', 'text/plain'))
        def changedResponse = operations.upload(UploadRequest.fromBytes('hello!'.bytes, 'changed.txt', 'text/plain'))

        then:
        firstResponse.ETag == sha256ETag('hello')
        firstResponse.ETag == secondResponse.ETag
        firstResponse.ETag != changedResponse.ETag
        metadataOperations.retrieve('first.txt').get().etag == firstResponse.ETag
    }

    void 'legacy metadata sidecars without a marker stay in legacy mode even for structured-looking keys'() {
        given:
        Path legacyFile = legacyObjectMetadataFile('legacy.txt')
        writeLegacyProperties(legacyFile)

        when:
        def entry = ctx.getBean(LocalStorageObjectMetadataOperations).retrieve('legacy.txt').get()

        then:
        entry.metadata == [
            'metadata.owner': 'ops',
            'attribute.region': 'eu-west-1',
            'system.contentType': 'text/plain',
        ]
        entry.attributes == [:]
        entry.contentType == null
        entry.contentLength == null
        entry.etag == null
        entry.lastModified == null
    }

    void 'structured sidecars write to the root object metadata namespace'() {
        given:
        ctx.getBean(LocalStorageOperations).upload(UploadRequest.fromBytes('hello'.bytes, 'structured.txt', 'text/plain'))
        def metadataOperations = ctx.getBean(LocalStorageObjectMetadataOperations)

        when:
        metadataOperations.save(new ObjectMetadataWrite(
            'structured.txt',
            ['metadata.owner': 'ops'],
            ['attribute.region': 'eu-west-1'],
            'text/plain',
            null,
            'etag-1',
            null
        ))
        Properties stored = new Properties()
        Files.newInputStream(rootObjectMetadataFile('structured.txt')).withCloseable {
            stored.load(it)
        }
        def entry = metadataOperations.retrieve('structured.txt').get()

        then:
        !Files.exists(legacyObjectMetadataFile('structured.txt'))
        stored.getProperty('micronaut.local.metadata.format') == 'structured-v1'
        entry.metadata == ['metadata.owner': 'ops']
        entry.attributes == ['attribute.region': 'eu-west-1']
        entry.contentType == 'text/plain'
        entry.etag == 'etag-1'
    }

    void 'stale temp cleanup does not delete object metadata sidecars with temp-like names'() {
        given:
        String victimKey = 'micronaut-object-storage-local-metadata-victim.tmp'
        String otherKey = 'unrelated.txt'
        def operations = ctx.getBean(LocalStorageOperations)
        def metadataOperations = ctx.getBean(LocalStorageObjectMetadataOperations)
        UploadRequest victimRequest = UploadRequest.fromBytes('victim'.bytes, victimKey, 'text/plain')
        victimRequest.metadata = [role: 'victim']
        operations.upload(victimRequest)
        operations.upload(UploadRequest.fromBytes('other'.bytes, otherKey, 'text/plain'))
        Path victimMetadataFile = rootObjectMetadataFile(victimKey)
        Files.setLastModifiedTime(victimMetadataFile, staleFileTime())

        when:
        metadataOperations.save(new ObjectMetadataWrite(
            otherKey,
            [role: 'other'],
            [:],
            'text/plain',
            null,
            null,
            null
        ))

        then:
        Files.exists(victimMetadataFile)
        metadataOperations.retrieve(victimKey).get().metadata == [role: 'victim']
    }

    void 'object metadata save removes stale matching temporary files'() {
        given:
        String key = 'metadata-cleanup.txt'
        def operations = ctx.getBean(LocalStorageOperations)
        def metadataOperations = ctx.getBean(LocalStorageObjectMetadataOperations)
        operations.upload(UploadRequest.fromBytes('content'.bytes, key, 'text/plain'))
        Path temporaryDirectory = objectMetadataTemporaryDirectory()
        Files.createDirectories(temporaryDirectory)
        Path staleTemporaryFile = temporaryDirectory.resolve('micronaut-object-storage-local-metadata-stale.tmp')
        Path freshTemporaryFile = temporaryDirectory.resolve('micronaut-object-storage-local-metadata-fresh.tmp')
        Path unrelatedTemporaryFile = temporaryDirectory.resolve('unrelated-stale.tmp')
        Files.writeString(staleTemporaryFile, 'stale')
        Files.writeString(freshTemporaryFile, 'fresh')
        Files.writeString(unrelatedTemporaryFile, 'unrelated')
        Files.setLastModifiedTime(staleTemporaryFile, staleFileTime())
        Files.setLastModifiedTime(unrelatedTemporaryFile, staleFileTime())

        when:
        metadataOperations.save(new ObjectMetadataWrite(
            key,
            [role: 'updated'],
            [:],
            'text/plain',
            null,
            null,
            null
        ))

        then:
        !Files.exists(staleTemporaryFile)
        Files.readString(freshTemporaryFile) == 'fresh'
        Files.readString(unrelatedTemporaryFile) == 'unrelated'
        metadataOperations.retrieve(key).get().metadata == [role: 'updated']
    }

    void 'object delete removes root and legacy metadata sidecars'() {
        given:
        Files.createDirectories(bucketPath)
        Files.writeString(bucketPath.resolve('delete-me.txt'), 'hello')
        Path rootFile = rootObjectMetadataFile('delete-me.txt')
        Path legacyFile = legacyObjectMetadataFile('delete-me.txt')
        writeLegacyProperties(rootFile)
        writeLegacyProperties(legacyFile)

        when:
        ctx.getBean(LocalStorageOperations).delete('delete-me.txt')

        then:
        !Files.exists(rootFile)
        !Files.exists(legacyFile)
    }

    @Requires({ FileSystems.default.supportedFileAttributeViews().contains('posix') })
    void 'object metadata retrieve reports canonical read errors instead of falling back to legacy sidecars'() {
        given:
        Files.createDirectories(bucketPath)
        Files.writeString(bucketPath.resolve('blocked-read.txt'), 'hello')
        Path rootFile = rootObjectMetadataFile('blocked-read.txt')
        Path legacyFile = legacyObjectMetadataFile('blocked-read.txt')
        writeLegacyProperties(rootFile)
        writeLegacyProperties(legacyFile)
        Path blockedDirectory = rootFile.parent
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(blockedDirectory)
        Files.setPosixFilePermissions(blockedDirectory, [] as Set<PosixFilePermission>)

        when:
        ctx.getBean(LocalStorageObjectMetadataOperations).retrieve('blocked-read.txt')

        then:
        def e = thrown(ObjectStorageException)
        e.message == 'Error reading metadata for object: blocked-read.txt'

        cleanup:
        restorePermissions(blockedDirectory, originalPermissions)
    }

    @Requires({ FileSystems.default.supportedFileAttributeViews().contains('posix') })
    void 'object metadata delete reports canonical delete errors instead of treating them as absent'() {
        given:
        Files.createDirectories(bucketPath)
        Files.writeString(bucketPath.resolve('blocked-delete.txt'), 'hello')
        Path rootFile = rootObjectMetadataFile('blocked-delete.txt')
        Path legacyFile = legacyObjectMetadataFile('blocked-delete.txt')
        writeLegacyProperties(rootFile)
        writeLegacyProperties(legacyFile)
        Path blockedDirectory = rootFile.parent
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(blockedDirectory)
        Files.setPosixFilePermissions(blockedDirectory, [] as Set<PosixFilePermission>)

        when:
        ctx.getBean(LocalStorageObjectMetadataOperations).delete('blocked-delete.txt')

        then:
        def e = thrown(ObjectStorageException)
        e.message == 'Error deleting metadata for object: blocked-delete.txt'

        cleanup:
        restorePermissions(blockedDirectory, originalPermissions)
    }

    private Path rootObjectMetadataFile(String key) {
        rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve(LocalStorageOperations.OBJECTS_DIRECTORY)
            .resolve('default')
            .resolve(key)
    }

    private Path objectMetadataTemporaryDirectory() {
        rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.TEMPORARY_DIRECTORY)
            .resolve('default')
            .resolve(LocalStorageLayout.OBJECT_METADATA_TEMPORARY_DIRECTORY)
    }

    private Path legacyObjectMetadataFile(String key) {
        bucketPath.resolve(LocalStorageOperations.LEGACY_METADATA_DIRECTORY)
            .resolve(key)
    }

    private static void writeLegacyProperties(Path path) {
        Files.createDirectories(path.parent)
        Properties legacy = new Properties()
        legacy.setProperty('metadata.owner', 'ops')
        legacy.setProperty('attribute.region', 'eu-west-1')
        legacy.setProperty('system.contentType', 'text/plain')
        Files.newOutputStream(path).withCloseable {
            legacy.store(it, 'legacy metadata')
        }
    }

    private static FileTime staleFileTime() {
        FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
    }

    private static void restorePermissions(Path directory, Set<PosixFilePermission> permissions) {
        if (directory != null && permissions != null && Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.setPosixFilePermissions(directory, permissions)
        }
    }

    private static String sha256ETag(String text) {
        MessageDigest.getInstance('SHA-256')
            .digest(text.bytes)
            .collect { String.format('%02x', it & 0xff) }
            .join()
    }
}
