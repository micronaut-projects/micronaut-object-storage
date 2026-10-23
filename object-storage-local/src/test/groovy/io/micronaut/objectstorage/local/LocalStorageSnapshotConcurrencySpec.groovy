package io.micronaut.objectstorage.local

import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class LocalStorageSnapshotConcurrencySpec extends Specification {

    void 'snapshot files are removed but their directory is retained after #operation'() {
        given:
        Path root = Files.createTempDirectory('local-snapshot-directory-retention')
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('probe')
        configuration.path = Files.createDirectory(root.resolve('bucket'))
        LocalStorageOperations operations = new LocalStorageOperations(configuration)
        Files.writeString(configuration.path.resolve('source.txt'), 'payload')
        if (operation in ['replace', 'delete']) {
            Files.writeString(configuration.path.resolve('object.txt'), 'original')
        }

        when:
        switch (operation) {
            case 'copy':
                operations.copy('source.txt', 'object.txt')
                break
            case 'delete':
            case 'missing delete':
                operations.delete('object.txt')
                break
            default:
                operations.upload(UploadRequest.fromBytes('payload'.bytes, 'object.txt'))
        }

        then:
        noExceptionThrown()
        Path snapshots = new LocalStorageLayout(configuration).snapshotBucketDirectory()
        Files.isDirectory(snapshots)
        Files.list(snapshots).withCloseable { it.findAny().isEmpty() }

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)

        where:
        operation << ['upload', 'replace', 'copy', 'delete', 'missing delete']
    }

    void 'multipart directory pruning retains the shared internal root'() {
        given:
        Path root = Files.createTempDirectory('local-internal-root-retention')
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('probe')
        configuration.path = root.resolve('bucket')
        LocalStorageLayout layout = new LocalStorageLayout(configuration)
        Files.createDirectories(layout.multipartBucketDirectory())

        when:
        layout.multipartCleanupDirectories().each { Files.deleteIfExists(it) }

        then:
        Files.isDirectory(layout.rootInternalDirectory())
        !Files.exists(layout.multipartBucketDirectory())

        cleanup:
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    private static boolean snapshotDirectoriesAreEmpty(Path root) {
        Files.walk(root.resolve('.mn-storage/snapshots')).withCloseable { paths ->
            paths.allMatch { Files.isDirectory(it) }
        }
    }

    void 'a blocked upload stream does not prevent another key from being uploaded'() {
        given:
        Path root = Files.createTempDirectory('local-snapshot-parallel-streams')
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('probe')
        configuration.path = Files.createDirectory(root.resolve('bucket'))
        LocalStorageOperations operations = new LocalStorageOperations(configuration)
        String blockedKey = 'blocked.txt'
        String otherKey = (0..<256).collect { "other-${it}.txt" }.find {
            LocalStorageLocks.objectMutationLockIndex(configuration.path.resolve(it)) !=
                LocalStorageLocks.objectMutationLockIndex(configuration.path.resolve(blockedKey))
        }
        CountDownLatch reading = new CountDownLatch(1)
        CountDownLatch finishReading = new CountDownLatch(1)
        InputStream delegate = new ByteArrayInputStream('payload'.bytes)
        InputStream input = new InputStream() {
            @Override
            int read() {
                delegate.read()
            }

            @Override
            int read(byte[] buffer, int offset, int length) {
                reading.countDown()
                assert finishReading.await(10, TimeUnit.SECONDS)
                delegate.read(buffer, offset, length)
            }
        }
        ExecutorService executor = Executors.newFixedThreadPool(2)

        when:
        Future<?> blocked = executor.submit({
            operations.upload(UploadRequest.fromInputStream(input, blockedKey))
        } as Callable)

        then:
        reading.await(5, TimeUnit.SECONDS)

        when:
        executor.submit({
            new LocalStorageOperations(configuration).upload(UploadRequest.fromBytes('other'.bytes, otherKey))
        } as Callable).get(5, TimeUnit.SECONDS)

        then:
        !blocked.done
        Files.readString(configuration.path.resolve(otherKey)) == 'other'

        when:
        finishReading.countDown()
        blocked.get(5, TimeUnit.SECONDS)

        then:
        Files.readString(configuration.path.resolve(blockedKey)) == 'payload'

        cleanup:
        finishReading?.countDown()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(10, TimeUnit.SECONDS)
        LocalStorageBucketOperations.deleteRecursively(root)
    }

    void 'concurrent #operation operations on distinct keys succeed across #bucketCount buckets'() {
        given:
        Path root = Files.createTempDirectory('local-snapshot-concurrency')
        List<Path> buckets = (0..<bucketCount).collect { Files.createDirectory(root.resolve("bucket-$it")) }
        // Separate provider instances must coordinate when they use the same directories.
        List<LocalStorageOperations> providers = buckets.collectMany { bucket ->
            (0..<2).collect {
                LocalStorageConfiguration configuration = new LocalStorageConfiguration('probe')
                configuration.path = bucket
                new LocalStorageOperations(configuration)
            }
        }
        buckets.each { Files.writeString(it.resolve('source.txt'), 'payload') }
        ExecutorService executor = Executors.newFixedThreadPool(24)

        when:
        10.times { round ->
            CountDownLatch ready = new CountDownLatch(24)
            CountDownLatch start = new CountDownLatch(1)
            List<Future<?>> tasks = (0..<24).collect { lane ->
                int providerIndex = lane % providers.size()
                Path bucket = buckets[providerIndex.intdiv(2)]
                LocalStorageOperations operations = providers[providerIndex]
                String key = "object-$round-$lane"
                if (operation in ['replace', 'delete']) {
                    Files.writeString(bucket.resolve(key), 'original')
                }
                executor.submit({
                    ready.countDown()
                    assert start.await(10, TimeUnit.SECONDS)
                    performOperationAndVerify(operation, operations, bucket, key)
                } as Callable)
            }
            try {
                assert ready.await(10, TimeUnit.SECONDS)
            } finally {
                start.countDown()
            }
            tasks.each { it.get(30, TimeUnit.SECONDS) }
        }

        then:
        noExceptionThrown()
        snapshotDirectoriesAreEmpty(root)

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(10, TimeUnit.SECONDS)
        LocalStorageBucketOperations.deleteRecursively(root)

        where:
        operation | bucketCount
        'upload'  | 1
        'upload'  | 2
        'replace' | 1
        'copy'    | 1
        'delete'  | 1
        'delete'  | 2
    }

    private static void performOperationAndVerify(String operation, LocalStorageOperations operations, Path bucket, String key) {
        switch (operation) {
            case 'copy':
                operations.copy('source.txt', key)
                break
            case 'delete':
                operations.delete(key)
                break
            default:
                operations.upload(UploadRequest.fromBytes('payload'.bytes, key))
        }
        if (operation == 'delete') {
            assert !Files.exists(bucket.resolve(key))
        } else {
            assert Files.readString(bucket.resolve(key)) == 'payload'
        }
    }
}
