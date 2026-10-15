package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class LocalStorageInternalDirectoryConfigurationSpec extends Specification {


    void 'custom internal directory contains provider state and is reserved'() {
        given:
        Path root = Files.createTempDirectory('local-custom-internal')
        Path bucket = root.resolve('default')
        LocalStorageConfiguration configuration = configuration(bucket)
        configuration.internalDirectory = '.storage-internal'
        LocalStorageLayout layout = new LocalStorageLayout(configuration)
        LocalStorageOperations operations = new LocalStorageOperations(configuration)
        LocalStorageMultipartOperations multipart = new LocalStorageMultipartOperations(configuration, operations)
        operations.upload(UploadRequest.fromBytes('content'.bytes, 'object.txt'))
        def created = multipart.createMultipartUpload(new CreateMultipartUploadRequest('multipart.txt'))

        expect:
        Files.exists(root.resolve('.storage-internal/metadata/objects/default/object.txt'))
        created.nativeResponse.path.startsWith(root.resolve('.storage-internal/multipart/default'))
        layout.objectTemporaryDirectory().startsWith(root.resolve('.storage-internal'))
        layout.snapshotBucketDirectory().startsWith(root.resolve('.storage-internal'))
        !Files.exists(root.resolve('.mn-storage'))

        when:
        Files.createDirectories(bucket.resolve('.storage-internal'))
        Files.writeString(bucket.resolve('.storage-internal/hidden'), 'internal')

        then:
        operations.listObjects() == ['object.txt'] as Set

        when:
        operations.upload(UploadRequest.fromBytes('bad'.bytes, '.STORAGE-INTERNAL/hidden'))

        then:
        thrown IllegalArgumentException

        when:
        layout.bucketPath('.storage-internal')

        then:
        thrown IllegalArgumentException

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'internal directory defaults and binds through configuration'() {
        given:
        Path root = Files.createTempDirectory('local-separate-roots')
        ApplicationContext context = ApplicationContext.run([
            (property('default', 'enabled')): true,
            (property('default', 'path')): root.resolve('first/default').toString(),
            (property('custom', 'path')): root.resolve('second/custom').toString(),
            (property('custom', 'internal-directory')): '.storage-internal'
        ])

        expect:
        configuration(context, 'default').internalDirectory == '.mn-storage'
        configuration(context, 'custom').internalDirectory == '.storage-internal'

        cleanup:
        context.close()
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'custom internal directory retains symlink protection'() {
        given:
        Path root = Files.createTempDirectory('local-custom-symlink')
        Path outside = Files.createDirectory(root.resolve('outside'))
        Files.createSymbolicLink(root.resolve('.storage-internal'), outside)
        LocalStorageConfiguration configuration = configuration(root.resolve('default'))
        configuration.internalDirectory = '.storage-internal'

        when:
        new LocalStorageOperations(configuration).upload(UploadRequest.fromBytes('content'.bytes, 'object.txt'))

        then:
        thrown IllegalArgumentException
        !Files.exists(outside.resolve('metadata'))
        !Files.exists(root.resolve('default/object.txt'))

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'unsafe internal directory names are rejected'() {
        when:
        new LocalStorageConfiguration('default').setInternalDirectory(name)

        then:
        thrown IllegalArgumentException

        where:
        name << ['', '.', '..', '../outside', '/absolute', 'nested/directory', 'nested\\directory', '.metadata', 'C:drive', 'with space']
    }


    private static LocalStorageConfiguration configuration(Path path) {
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('default')
        configuration.path = path
        configuration
    }

    private static LocalStorageConfiguration configuration(ApplicationContext context, String name) {
        context.getBean(LocalStorageConfiguration, Qualifiers.byName(name))
    }

    private static String property(String name, String property) {
        "${LocalStorageConfiguration.PREFIX}.${name}.${property}"
    }
}
