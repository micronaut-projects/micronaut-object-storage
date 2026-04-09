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

    void 'legacy sidecars without a marker stay in legacy mode even for structured-looking keys'() {
        given:
        ctx.getBean(LocalStorageOperations).upload(UploadRequest.fromBytes('hello'.bytes, 'legacy.txt', 'text/plain'))
        Path legacyFile = bucketPath.resolve(LocalStorageOperations.METADATA_DIRECTORY).resolve('legacy.txt')
        Files.createDirectories(legacyFile.parent)
        Properties legacy = new Properties()
        legacy.setProperty('metadata.owner', 'ops')
        legacy.setProperty('attribute.region', 'eu-west-1')
        legacy.setProperty('system.contentType', 'text/plain')
        Files.newOutputStream(legacyFile).withCloseable {
            legacy.store(it, 'legacy metadata')
        }

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

    void 'structured sidecars write an explicit marker and preserve prefixed metadata keys'() {
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
        Files.newInputStream(bucketPath.resolve(LocalStorageOperations.METADATA_DIRECTORY).resolve('structured.txt')).withCloseable {
            stored.load(it)
        }
        def entry = metadataOperations.retrieve('structured.txt').get()

        then:
        stored.getProperty('micronaut.local.metadata.format') == 'structured-v1'
        entry.metadata == ['metadata.owner': 'ops']
        entry.attributes == ['attribute.region': 'eu-west-1']
        entry.contentType == 'text/plain'
        entry.etag == 'etag-1'
    }
}
