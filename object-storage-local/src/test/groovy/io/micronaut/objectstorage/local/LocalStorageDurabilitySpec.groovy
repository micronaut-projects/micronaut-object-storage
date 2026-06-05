package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class LocalStorageDurabilitySpec extends Specification {

    private Path rootDirectory
    private Path defaultBucketPath
    private ApplicationContext ctx
    private LocalStorageOperations operations

    void setup() {
        rootDirectory = Files.createTempDirectory('LocalStorageDurabilitySpec')
        defaultBucketPath = rootDirectory.resolve('default')
        ctx = ApplicationContext.run([
            'micronaut.object-storage.local.default.path': defaultBucketPath.toString()
        ])
        operations = ctx.getBean(LocalStorageOperations)
    }

    void cleanup() {
        ctx?.close()
        if (rootDirectory != null && Files.exists(rootDirectory)) {
            LocalStorageBucketOperations.deleteRecursively(rootDirectory)
        }
    }

    void 'replacement upload does not expose partial bytes while input stream is still writing'() {
        given:
        operations.upload(UploadRequest.fromBytes(bytes('original'), 'object.txt', 'text/plain'))
        CountDownLatch writeStarted = new CountDownLatch(1)
        CountDownLatch allowWriteToFinish = new CountDownLatch(1)
        ExecutorService executor = Executors.newSingleThreadExecutor()
        Future<?> upload = null

        when:
        upload = executor.submit({
            operations.upload(new BlockingUploadRequest(bytes('replacement'), 'object.txt', writeStarted, allowWriteToFinish))
        } as Callable)

        then:
        writeStarted.await(5, TimeUnit.SECONDS)
        text('object.txt') == 'original'
        operations.listObjects() == ['object.txt'] as Set

        when:
        allowWriteToFinish.countDown()
        upload.get(5, TimeUnit.SECONDS)

        then:
        text('object.txt') == 'replacement'
        temporaryFiles(defaultTemporaryDirectory()).empty

        cleanup:
        allowWriteToFinish.countDown()
        executor?.shutdownNow()
    }

    void 'new upload does not expose object while input stream is still writing'() {
        given:
        CountDownLatch writeStarted = new CountDownLatch(1)
        CountDownLatch allowWriteToFinish = new CountDownLatch(1)
        ExecutorService executor = Executors.newSingleThreadExecutor()
        Future<?> upload = null

        when:
        upload = executor.submit({
            operations.upload(new BlockingUploadRequest(bytes('created'), 'created.txt', writeStarted, allowWriteToFinish))
        } as Callable)

        then:
        writeStarted.await(5, TimeUnit.SECONDS)
        !operations.retrieve('created.txt').present
        !operations.listObjects().contains('created.txt')

        when:
        allowWriteToFinish.countDown()
        upload.get(5, TimeUnit.SECONDS)

        then:
        text('created.txt') == 'created'
        operations.listObjects() == ['created.txt'] as Set
        temporaryFiles(defaultTemporaryDirectory()).empty

        cleanup:
        allowWriteToFinish.countDown()
        executor?.shutdownNow()
    }

    void 'write and replace keeps target visible until temporary file is moved'() {
        given:
        Path directory = Files.createTempDirectory(rootDirectory, 'atomic')
        Path target = directory.resolve('metadata.properties')
        Files.writeString(target, 'old', StandardCharsets.UTF_8)
        CountDownLatch writeStarted = new CountDownLatch(1)
        CountDownLatch allowWriteToFinish = new CountDownLatch(1)
        ExecutorService executor = Executors.newSingleThreadExecutor()
        Future<?> write = null

        when:
        write = executor.submit({
            LocalStorageIoSupport.writeAndReplace(target, directory, 'metadata', '.tmp', false, { output ->
                writeStarted.countDown()
                await(allowWriteToFinish)
                output.write(bytes('new'))
            })
        } as Callable)

        then:
        writeStarted.await(5, TimeUnit.SECONDS)
        Files.readString(target, StandardCharsets.UTF_8) == 'old'

        when:
        allowWriteToFinish.countDown()
        write.get(5, TimeUnit.SECONDS)

        then:
        Files.readString(target, StandardCharsets.UTF_8) == 'new'
        temporaryFiles(directory).empty

        cleanup:
        allowWriteToFinish.countDown()
        executor?.shutdownNow()
    }

    void 'write and replace removes temporary file when writer fails'() {
        given:
        Path directory = Files.createTempDirectory(rootDirectory, 'atomic-failure')
        Path target = directory.resolve('metadata.properties')
        Files.writeString(target, 'old', StandardCharsets.UTF_8)

        when:
        LocalStorageIoSupport.writeAndReplace(target, directory, 'metadata', '.tmp', false, { output ->
            output.write(bytes('partial'))
            throw new IOException('write failed')
        })

        then:
        IOException e = thrown()
        e.message == 'write failed'
        Files.readString(target, StandardCharsets.UTF_8) == 'old'
        temporaryFiles(directory).empty
    }

    private String text(String key) {
        operations.retrieve(key).get().inputStream.withCloseable { input ->
            new String(input.readAllBytes(), StandardCharsets.UTF_8)
        }
    }

    private static byte[] bytes(String value) {
        value.getBytes(StandardCharsets.UTF_8)
    }

    private Path defaultTemporaryDirectory() {
        rootDirectory.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageLayout.TEMPORARY_DIRECTORY)
            .resolve('default')
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

    private static List<Path> temporaryFiles(Path directory) {
        if (!Files.exists(directory)) {
            return []
        }
        Files.list(directory).withCloseable { stream ->
            stream.filter { path -> path.fileName.toString().endsWith('.tmp') }.toList()
        }
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
