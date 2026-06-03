package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

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

    private Path rootObjectMetadataFile(String key) {
        rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve(LocalStorageOperations.OBJECTS_DIRECTORY)
            .resolve('default')
            .resolve(key)
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
}
