package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.metadata.BucketMetadataOperations
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.metadata.ObjectMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataWrite
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class LocalStorageMetadataModeSpec extends Specification {

    void 'metadata mode defaults and binds for multiple named configurations'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            (property('default', 'enabled'))      : true,
            (property('metadata', 'enabled'))      : true,
            (property('metadata', 'metadata-mode')): 'enabled',
            (property('none', 'enabled'))         : true,
            (property('none', 'metadata-mode'))   : 'none'
        ])

        expect:
        configuration(context, 'default').metadataMode == LocalStorageMetadataMode.ENABLED
        configuration(context, 'metadata').metadataMode == LocalStorageMetadataMode.ENABLED
        configuration(context, 'none').metadataMode == LocalStorageMetadataMode.NONE

        cleanup:
        context.close()
    }

    void 'invalid metadata mode fails configuration binding clearly'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            (property('default', 'enabled'))      : true,
            (property('default', 'metadata-mode')): 'database'
        ])

        when:
        configuration(context, 'default')

        then:
        Exception exception = thrown()
        exception.message.contains('metadata-mode') || exception.message.contains('metadataMode')
        exception.message.contains('database')

        cleanup:
        context.close()
    }

    void 'none upload returns deterministic content etag without persisting metadata'() {
        given:
        Path root = Files.createTempDirectory('local-none-upload')
        LocalStorageConfiguration configuration = noneConfiguration(root.resolve('default'))
        LocalStorageOperations operations = new LocalStorageOperations(configuration)

        when:
        def first = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'first.txt', 'text/plain'))
        def repeated = operations.upload(UploadRequest.fromBytes('hello'.bytes, 'second.txt', 'text/plain'))
        def changed = operations.upload(UploadRequest.fromBytes('changed'.bytes, 'third.txt', 'text/plain'))
        def entry = operations.retrieve('first.txt').get()

        then:
        first.ETag == sha256ETag('hello')
        repeated.ETag == first.ETag
        changed.ETag != first.ETag
        entry.inputStream.text == 'hello'
        entry.metadata.isEmpty()
        entry.contentType.get() == 'text/plain'
        !Files.exists(objectSidecar(root, 'first.txt'))
        !Files.exists(legacySidecar(root.resolve('default'), 'first.txt'))

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'explicit enabled mode preserves built-in metadata behavior'() {
        given:
        Path root = Files.createTempDirectory('local-explicit-metadata')
        LocalStorageOperations operations = new LocalStorageOperations(enabledConfiguration(root.resolve('default')))
        def request = UploadRequest.fromBytes('content'.bytes, 'object.txt')
        request.metadata = [owner: 'micronaut']

        when:
        def response = operations.upload(request)
        def entry = operations.retrieve('object.txt').get()

        then:
        response.ETag == sha256ETag('content')
        entry.metadata == [owner: 'micronaut']
        Files.exists(objectSidecar(root, 'object.txt'))

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'none rejects metadata before overwriting an existing object'() {
        given:
        Path root = Files.createTempDirectory('local-none-reject')
        LocalStorageConfiguration configuration = noneConfiguration(root.resolve('default'))
        LocalStorageOperations operations = new LocalStorageOperations(configuration)
        operations.upload(UploadRequest.fromBytes('original'.bytes, 'object.txt'))
        def rejected = UploadRequest.fromBytes('replacement'.bytes, 'object.txt')
        rejected.metadata = [owner: 'application']

        when:
        operations.upload(rejected)

        then:
        ObjectStorageException exception = thrown()
        exception.message == 'Local storage metadata mode NONE does not support user metadata'
        operations.retrieve('object.txt').get().inputStream.text == 'original'

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'none bypasses an injected metadata provider'() {
        given:
        Path root = Files.createTempDirectory('local-none-custom-provider')
        LocalStorageConfiguration configuration = noneConfiguration(root.resolve('default'))
        ObjectMetadataOperations<Path> metadataOperations = Mock()
        LocalStorageOperations operations = new LocalStorageOperations(configuration, metadataOperations)

        when:
        operations.upload(UploadRequest.fromBytes('content'.bytes, 'object.txt'))
        operations.retrieve('object.txt')
        operations.delete('object.txt')

        then:
        0 * metadataOperations._

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'none built-in metadata operations never read or save sidecars'() {
        given:
        Path root = Files.createTempDirectory('local-none-metadata-operations')
        Path bucket = root.resolve('default')
        Files.createDirectories(bucket)
        Path objectSidecar = objectSidecar(root, 'object.txt')
        Path bucketSidecar = root.resolve('.mn-storage/metadata/buckets/default')
        Files.createDirectories(objectSidecar.parent)
        Files.createDirectories(bucketSidecar.parent)
        Files.writeString(objectSidecar, 'owner=stale')
        Files.writeString(bucketSidecar, 'owner=stale')
        LocalStorageConfiguration configuration = noneConfiguration(bucket)
        LocalStorageObjectMetadataOperations objects = new LocalStorageObjectMetadataOperations(configuration)
        LocalStorageBucketMetadataOperations buckets = new LocalStorageBucketMetadataOperations(configuration)

        expect:
        objects.retrieve('object.txt').empty
        buckets.retrieve('default').empty

        when:
        objects.save(new ObjectMetadataWrite('object.txt', [owner: 'application']))

        then:
        ObjectStorageException objectException = thrown()
        objectException.message == 'Local storage metadata mode NONE does not support object metadata persistence'

        when:
        buckets.save(new BucketMetadataWrite('default', [owner: 'application']))

        then:
        ObjectStorageException bucketException = thrown()
        bucketException.message == 'Local storage metadata mode NONE does not support bucket metadata persistence'
        Files.exists(objectSidecar)
        Files.exists(bucketSidecar)

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'none copy and delete clean stale current and legacy sidecars'() {
        given:
        Path root = Files.createTempDirectory('local-none-stale')
        Path bucket = root.resolve('default')
        LocalStorageConfiguration enabledConfig = enabledConfiguration(bucket)
        LocalStorageOperations enabledOperations = new LocalStorageOperations(enabledConfig)
        def sourceRequest = UploadRequest.fromBytes('source'.bytes, 'source.txt')
        sourceRequest.metadata = [owner: 'source']
        def destinationRequest = UploadRequest.fromBytes('destination'.bytes, 'destination.txt')
        destinationRequest.metadata = [owner: 'destination']
        enabledOperations.upload(sourceRequest)
        enabledOperations.upload(destinationRequest)
        Path legacy = legacySidecar(bucket, 'destination.txt')
        Files.createDirectories(legacy.parent)
        Files.writeString(legacy, 'owner=legacy')

        and:
        LocalStorageOperations noneOperations = new LocalStorageOperations(noneConfiguration(bucket))

        when:
        noneOperations.copy('source.txt', 'destination.txt')

        then:
        noneOperations.retrieve('destination.txt').get().inputStream.text == 'source'
        noneOperations.retrieve('destination.txt').get().metadata.isEmpty()
        !Files.exists(objectSidecar(root, 'destination.txt'))
        !Files.exists(legacy)
        Files.exists(objectSidecar(root, 'source.txt'))

        when:
        noneOperations.delete('destination.txt')
        noneOperations.upload(UploadRequest.fromBytes('recreated'.bytes, 'destination.txt'))
        LocalStorageOperations switchedBack = new LocalStorageOperations(enabledConfiguration(bucket))

        then:
        switchedBack.retrieve('destination.txt').get().metadata.isEmpty()

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'none multipart rejects metadata before state creation and completes metadata-free uploads'() {
        given:
        Path root = Files.createTempDirectory('local-none-multipart')
        LocalStorageConfiguration configuration = noneConfiguration(root.resolve('default'))
        LocalStorageOperations operations = new LocalStorageOperations(configuration)
        LocalStorageMultipartOperations multipart = new LocalStorageMultipartOperations(configuration, operations)
        Path multipartRoot = root.resolve('.mn-storage/multipart/default')

        when:
        multipart.createMultipartUpload(new CreateMultipartUploadRequest('rejected.txt', 'text/plain', [owner: 'application']))

        then:
        ObjectStorageException exception = thrown()
        exception.message == 'Local storage metadata mode NONE does not support user metadata'
        !Files.exists(multipartRoot)

        when:
        def created = multipart.createMultipartUpload(new CreateMultipartUploadRequest('completed.txt', 'text/plain'))
        def part = multipart.uploadPart(new UploadPartRequest(
            created.upload,
            1,
            UploadRequest.fromBytes('content'.bytes, 'completed.txt', 'text/plain')
        ))
        def completed = multipart.completeMultipartUpload(new CompleteMultipartUploadRequest(created.upload, [part.part]))

        then:
        completed.ETag == sha256ETag('content')
        operations.retrieve('completed.txt').get().inputStream.text == 'content'
        operations.retrieve('completed.txt').get().metadata.isEmpty()
        !Files.exists(objectSidecar(root, 'completed.txt'))
        !Files.exists(created.nativeResponse.path)

        when:
        def aborted = multipart.createMultipartUpload(new CreateMultipartUploadRequest('aborted.txt'))
        multipart.abortMultipartUpload(new AbortMultipartUploadRequest(aborted.upload))

        then:
        !Files.exists(aborted.nativeResponse.path)

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'none bucket lifecycle creates no metadata sidecar and cleans provider working state'() {
        given:
        Path root = Files.createTempDirectory('local-none-bucket')
        LocalStorageConfiguration configuration = noneConfiguration(root.resolve('default'))
        BucketMetadataOperations<Path> metadataOperations = Mock()
        LocalStorageBucketOperations buckets = new LocalStorageBucketOperations(configuration, metadataOperations)
        Path workingState = root.resolve('.mn-storage/multipart/other/upload')

        when:
        buckets.create('other')
        Files.createDirectories(workingState)
        Files.writeString(workingState.resolve('state'), 'state')

        then:
        buckets.retrieve('other').present
        !Files.exists(root.resolve('.mn-storage/metadata/buckets/other'))

        when:
        buckets.delete('other')

        then:
        !buckets.retrieve('other').present
        !Files.exists(workingState)
        0 * metadataOperations._

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    private static LocalStorageConfiguration configuration(ApplicationContext context, String name) {
        context.getBean(LocalStorageConfiguration, Qualifiers.byName(name))
    }

    private static LocalStorageConfiguration noneConfiguration(Path path) {
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('default')
        configuration.path = path
        configuration.metadataMode = LocalStorageMetadataMode.NONE
        configuration
    }

    private static LocalStorageConfiguration enabledConfiguration(Path path) {
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('default')
        configuration.path = path
        configuration.metadataMode = LocalStorageMetadataMode.ENABLED
        configuration
    }

    private static String property(String name, String property) {
        "${LocalStorageConfiguration.PREFIX}.${name}.${property}"
    }

    private static Path objectSidecar(Path root, String key) {
        root.resolve('.mn-storage/metadata/objects/default').resolve(key)
    }

    private static Path legacySidecar(Path bucket, String key) {
        bucket.resolve('.metadata').resolve(key)
    }

    private static String sha256ETag(String text) {
        MessageDigest.getInstance('SHA-256').digest(text.bytes).encodeHex().toString()
    }
}
