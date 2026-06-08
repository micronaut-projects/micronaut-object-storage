package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.BucketOperationsSpecification
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.bucket.BucketOperations
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock

class LocalStorageBucketOperationsSpec extends BucketOperationsSpecification {

    private Path rootDirectory
    private Path defaultBucketPath
    private ApplicationContext ctx

    void setup() {
        rootDirectory = Files.createTempDirectory('LocalStorageBucketOperationsSpec')
        defaultBucketPath = rootDirectory.resolve('default')
        ctx = ApplicationContext.run([
            'micronaut.object-storage.local.default.path': defaultBucketPath.toString()
        ])
    }

    void cleanup() {
        ctx?.close()
        if (rootDirectory != null && Files.exists(rootDirectory)) {
            LocalStorageBucketOperations.deleteRecursively(rootDirectory)
        }
    }

    @Override
    BucketOperations<?> getBucketOperations() {
        ctx.getBean(LocalStorageBucketOperations)
    }

    @Override
    ObjectStorageOperations<?, ?, ?> getObjectStorage() {
        ctx.getBean(LocalStorageOperations)
    }

    @Override
    String newBucketName() {
        "bucket-${System.nanoTime()}"
    }

    void 'it rejects bucket names with path traversal'() {
        when:
        getBucketOperations().create('..')

        then:
        thrown IllegalArgumentException

        when:
        getBucketOperations().create('foo/bar')

        then:
        thrown IllegalArgumentException
    }

    void 'it rejects reserved bucket names'() {
        when:
        getBucketOperations().create(name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Bucket name uses the reserved ${reservedNamespace} namespace: ${name}"

        where:
        name          | reservedNamespace
        '.mn-storage' | '.mn-storage'
        '.Mn-Storage' | '.mn-storage'
        '.metadata'   | '.metadata'
        '.Metadata'   | '.metadata'
    }

    void 'failed bucket delete preserves metadata sidecars'() {
        given:
        Path bucket = rootDirectory.resolve('stuck')
        Files.writeString(bucket, 'not-a-directory')
        Path metadataFile = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve('buckets')
            .resolve('stuck')
        Files.createDirectories(metadataFile.parent)
        Files.writeString(metadataFile, 'owner=cliponaut')

        when:
        getBucketOperations().delete('stuck')

        then:
        thrown IllegalStateException
        Files.exists(metadataFile)
    }

    void 'bucket delete removes provider managed metadata multipart state snapshots and temporary files'() {
        given:
        Path objectMetadataDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve(LocalStorageOperations.OBJECTS_DIRECTORY)
            .resolve('default')
        Path multipartDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.MULTIPART_DIRECTORY)
            .resolve('default')
        Path snapshotDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.SNAPSHOT_DIRECTORY)
            .resolve('default')
        Path temporaryDirectory = rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.TEMPORARY_DIRECTORY)
            .resolve('default')
        getObjectStorage().upload(UploadRequest.fromBytes('hello'.bytes, 'delete-me.txt', 'text/plain'))
        def multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        def multipartUpload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest('multipart/delete-me.txt', 'text/plain')).upload
        multipartOperations.uploadPart(new UploadPartRequest(multipartUpload, 1, UploadRequest.fromBytes('part'.bytes, 'multipart/delete-me.txt', 'text/plain')))
        Files.createDirectories(snapshotDirectory)
        Files.writeString(snapshotDirectory.resolve('temporary'), 'snapshot')

        expect:
        Files.exists(objectMetadataDirectory.resolve('delete-me.txt'))
        Files.exists(multipartDirectory.resolve(multipartUpload.uploadId))
        Files.exists(snapshotDirectory.resolve('temporary'))
        Files.exists(temporaryDirectory)

        when:
        getBucketOperations().delete('default')

        then:
        !Files.exists(defaultBucketPath)
        !Files.exists(objectMetadataDirectory)
        !Files.exists(multipartDirectory)
        !Files.exists(snapshotDirectory)
        !Files.exists(temporaryDirectory)
        !ctx.getBean(LocalStorageObjectMetadataOperations).retrieve('delete-me.txt').present
    }

    void 'bucket delete waits for an in-flight object mutation'() {
        given:
        CountDownLatch writeStarted = new CountDownLatch(1)
        CountDownLatch allowWriteToFinish = new CountDownLatch(1)
        CountDownLatch deleteStarted = new CountDownLatch(1)
        ExecutorService executor = Executors.newFixedThreadPool(2)
        Future<?> upload = null
        Future<?> delete = null

        when:
        upload = executor.submit({
            getObjectStorage().upload(new BlockingUploadRequest('held'.bytes, 'held.txt', writeStarted, allowWriteToFinish))
        } as Callable)

        then:
        writeStarted.await(5, TimeUnit.SECONDS) == true

        when:
        delete = executor.submit({
            deleteStarted.countDown()
            getBucketOperations().delete('default')
        } as Callable)

        then:
        deleteStarted.await(5, TimeUnit.SECONDS) == true

        and:
        assertQueuedBucketDeleteBlocksNewReaders(defaultBucketPath)
        Files.exists(defaultBucketPath)

        when:
        allowWriteToFinish.countDown()
        upload.get(5, TimeUnit.SECONDS)
        delete.get(5, TimeUnit.SECONDS)

        then:
        !Files.exists(defaultBucketPath)

        cleanup:
        allowWriteToFinish?.countDown()
        executor?.shutdownNow()
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException('Timed out waiting for test writer to finish')
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new IOException('Interrupted while waiting for test writer to finish', e)
        }
    }

    private static void assertQueuedBucketDeleteBlocksNewReaders(Path bucketPath) throws IOException {
        Lock readLock = LocalStorageLocks.bucketReadLock(bucketPath)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            try {
                if (!readLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    return
                }
                readLock.unlock()
                TimeUnit.MILLISECONDS.sleep(10)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new IOException('Interrupted while waiting for queued bucket delete', e)
            }
        }
        throw new AssertionError('Timed out waiting for bucket delete to queue behind object mutation')
    }

    private static final class BlockingUploadRequest implements UploadRequest {
        private final byte[] bytes
        private final String key
        private final CountDownLatch writeStarted
        private final CountDownLatch allowWriteToFinish

        private BlockingUploadRequest(byte[] bytes,
                                      String key,
                                      CountDownLatch writeStarted,
                                      CountDownLatch allowWriteToFinish) {
            this.bytes = bytes
            this.key = key
            this.writeStarted = writeStarted
            this.allowWriteToFinish = allowWriteToFinish
        }

        @Override
        Optional<String> getContentType() {
            Optional.of('text/plain')
        }

        @Override
        String getKey() {
            key
        }

        @Override
        Optional<Long> getContentSize() {
            Optional.of(bytes.length as Long)
        }

        @Override
        InputStream getInputStream() {
            new BlockingInputStream(bytes, writeStarted, allowWriteToFinish)
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final byte[] bytes
        private final CountDownLatch writeStarted
        private final CountDownLatch allowWriteToFinish
        private int index

        private BlockingInputStream(byte[] bytes,
                                    CountDownLatch writeStarted,
                                    CountDownLatch allowWriteToFinish) {
            this.bytes = bytes
            this.writeStarted = writeStarted
            this.allowWriteToFinish = allowWriteToFinish
        }

        @Override
        int read() throws IOException {
            if (index == bytes.length) {
                return -1
            }
            if (index == 0) {
                writeStarted.countDown()
                await(allowWriteToFinish)
            }
            bytes[index++] & 0xff
        }
    }
}
