package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class LocalStorageInternalDirectorySpec extends Specification {

    void 'startup rejects buckets nested inside provider state'() {
        given:
        Path root = Files.createTempDirectory('local-nested-collision')
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'micronaut.object-storage.local.a.path': root.resolve('docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': '.storage-internal',
            'micronaut.object-storage.local.b.path': root.resolve('.storage-internal/metadata/objects/docs').toString()
        ])

        then:
        def e = thrown(Exception)
        e.message.contains('collides with configured bucket')
        !Files.exists(root.resolve('.storage-internal'))
        !Files.exists(root.resolve('docs'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'startup rejects provider state nested inside a configured bucket'() {
        given:
        Path root = Files.createTempDirectory('local-provider-inside-bucket')
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'micronaut.object-storage.local.a.path': root.resolve('outer/docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': '.storage-internal',
            'micronaut.object-storage.local.b.path': root.resolve('outer').toString()
        ])

        then:
        def e = thrown(Exception)
        e.message.contains('collides with configured bucket')
        !Files.exists(root.resolve('outer'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'root aliases resolve consistently before directories exist'() {
        given:
        Path root = Files.createTempDirectory('local-aliased-root')
        Path real = Files.createDirectory(root.resolve('real'))
        Path alias = Files.createSymbolicLink(root.resolve('alias'), real)
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'micronaut.object-storage.local.a.path': real.resolve('new/docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': '.storage-internal',
            'micronaut.object-storage.local.b.path': alias.resolve('new/photos').toString(),
            'micronaut.object-storage.local.b.internal-directory': otherInternal
        ])

        then:
        def e = thrown(Exception)
        e.message.contains('different internal-directory')
        !Files.exists(real.resolve('new'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)

        where:
        otherInternal << ['.mn-storage', '.other-internal']
    }

    void 'matching configurations through aliases can start before creating storage'() {
        given:
        Path root = Files.createTempDirectory('local-compatible-alias')
        Path real = Files.createDirectory(root.resolve('real'))
        Path alias = Files.createSymbolicLink(root.resolve('alias'), real)
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'micronaut.object-storage.local.a.path': real.resolve('new/docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': '.storage-internal',
            'micronaut.object-storage.local.b.path': alias.resolve('new/photos').toString(),
            'micronaut.object-storage.local.b.internal-directory': '.storage-internal'
        ])

        then:
        context.isRunning()
        !Files.exists(real.resolve('new'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'startup rejects conflicting internal names on a shared root'() {
        given:
        Path root = Files.createTempDirectory('local-root-conflict')
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'micronaut.object-storage.local.a.path': root.resolve('docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': 'photos',
            'micronaut.object-storage.local.b.path': root.resolve('photos').toString()
        ])

        then:
        def e = thrown(Exception)
        e.message.contains('different internal-directory')
        !Files.exists(root.resolve('photos'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'startup rejects a configured bucket even if it resembles provider state'() {
        given:
        Path root = Files.createTempDirectory('local-bucket-conflict')
        Files.createDirectories(root.resolve('photos/metadata/objects'))
        ApplicationContext context = null

        when:
        context = ApplicationContext.run([
            'micronaut.object-storage.local.a.path': root.resolve('docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': 'photos',
            'micronaut.object-storage.local.b.path': root.resolve('photos').toString(),
            'micronaut.object-storage.local.b.internal-directory': 'photos'
        ])

        then:
        def e = thrown(Exception)
        e.message.contains('collides with configured bucket')
        Files.isDirectory(root.resolve('photos/metadata/objects'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'compatible configurations share custom state and can reopen it'() {
        given:
        Path root = Files.createTempDirectory('local-shared-state')
        Map properties = [
            'micronaut.object-storage.local.a.path': root.resolve('docs').toString(),
            'micronaut.object-storage.local.a.internal-directory': '.storage-internal',
            'micronaut.object-storage.local.b.path': root.resolve('photos').toString(),
            'micronaut.object-storage.local.b.internal-directory': '.storage-internal',
            'micronaut.object-storage.local.disabled.path': root.resolve('unused').toString(),
            'micronaut.object-storage.local.disabled.enabled': false
        ]
        ApplicationContext context = ApplicationContext.run(properties)
        new LocalStorageOperations(context.getBean(LocalStorageConfiguration, Qualifiers.byName('a')))
            .upload(UploadRequest.fromBytes('content'.bytes, 'doc.txt'))
        context.close()

        when:
        context = ApplicationContext.run(properties)

        then:
        new LocalStorageOperations(context.getBean(LocalStorageConfiguration, Qualifiers.byName('a'))).retrieve('doc.txt').present
        Files.exists(root.resolve('.storage-internal/metadata/objects/docs/doc.txt'))
        !Files.exists(root.resolve('.mn-storage'))

        cleanup:
        context?.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'existing custom directories reject unexpected entries without mutation'() {
        given:
        Path root = Files.createTempDirectory('local-unowned-state')
        Path internal = root.resolve('.storage-internal')
        Path entry = internal.resolve(relativeEntry)
        Files.createDirectories(entry.parent)
        if (directory) {
            Files.createDirectory(entry)
        } else {
            Files.writeString(entry, 'user content')
        }
        LocalStorageConfiguration configuration = configuration(root)

        when:
        new LocalStorageOperations(configuration)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('unexpected provider layout entry')
        Files.exists(entry)
        !Files.exists(root.resolve('docs'))

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)

        where:
        relativeEntry          | directory
        'photo.jpg'            | false
        'metadata'             | false
        'other'                | true
        'metadata/unexpected'   | true
        'metadata/objects'      | false
    }

    void 'empty and partial custom layouts are accepted'() {
        given:
        Path root = Files.createTempDirectory('local-partial-layout')
        Files.createDirectories(root.resolve('.storage-internal').resolve(relativeDirectory))

        expect:
        new LocalStorageLayout(configuration(root)).rootInternalDirectory() == root.resolve('.storage-internal')

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)

        where:
        relativeDirectory << ['', 'metadata/objects', 'metadata/buckets', 'multipart', 'tmp', 'snapshots']
    }

    void 'default layout remains compatible with existing entries'() {
        given:
        Path root = Files.createTempDirectory('local-default-layout')
        Path existing = root.resolve('.mn-storage/existing')
        Files.createDirectories(existing.parent)
        Files.writeString(existing, 'existing state')
        LocalStorageConfiguration configuration = configuration(root)
        configuration.internalDirectory = '.mn-storage'

        expect:
        new LocalStorageLayout(configuration).rootInternalDirectory() == existing.parent
        Files.readString(existing) == 'existing state'

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'metadata read and cleanup do not follow or validate object symlinks'() {
        given:
        Path root = Files.createTempDirectory('local-metadata-independent')
        LocalStorageConfiguration configuration = configuration(root)
        new LocalStorageOperations(configuration).upload(UploadRequest.fromBytes('content'.bytes, 'doc.txt'))
        Path object = root.resolve('docs/doc.txt')
        Files.delete(object)
        Path outside = root.resolve('outside')
        Files.writeString(outside, 'protected')
        Files.createSymbolicLink(object, outside)
        LocalStorageObjectMetadataOperations metadata = new LocalStorageObjectMetadataOperations(configuration)

        expect:
        metadata.retrieve('doc.txt').present

        when:
        metadata.delete('doc.txt')

        then:
        !metadata.retrieve('doc.txt').present
        Files.readString(outside) == 'protected'
        Files.isSymbolicLink(object)

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    private static LocalStorageConfiguration configuration(Path root) {
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('default')
        configuration.path = root.resolve('docs')
        configuration.internalDirectory = '.storage-internal'
        configuration
    }
}
