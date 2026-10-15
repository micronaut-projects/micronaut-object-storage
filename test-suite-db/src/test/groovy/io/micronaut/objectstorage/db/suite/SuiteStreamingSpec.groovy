package io.micronaut.objectstorage.db.suite

import io.micronaut.objectstorage.db.*

import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors

@MicronautTest(transactional = false)
class SuiteStreamingSpec extends Specification implements TestPropertyProvider {

    @Inject
    DbStorageOperations storage

    @Override
    Map<String, String> getProperties() {
        SuiteProperties.storage('streaming', ['chunk-size': '4', 'max-buffered-bytes': '16', 'max-object-size': '1KB'])
    }

    void 'a download fails when the object is overwritten while it is being read'() {
        given:
        storage.upload(UploadRequest.fromBytes(('a' * 400).bytes, 'big.txt'))
        InputStream stream = storage.retrieve('big.txt').get().inputStream

        when:
        stream.read()
        storage.upload(UploadRequest.fromBytes(('b' * 400).bytes, 'big.txt'))
        stream.readAllBytes()

        then:
        def e = thrown(IOException)
        e.message.contains('changed or was deleted')

        cleanup:
        stream?.close()
    }

    void 'a download fails when the object is deleted while it is being read'() {
        given:
        storage.upload(UploadRequest.fromBytes(('a' * 400).bytes, 'gone.txt'))
        InputStream stream = storage.retrieve('gone.txt').get().inputStream

        when:
        stream.read()
        storage.delete('gone.txt')
        stream.readAllBytes()

        then:
        thrown(IOException)

        cleanup:
        stream?.close()
    }

    void 'each call to getInputStream opens a new stream'() {
        given:
        storage.upload(UploadRequest.fromBytes('hello world'.bytes, 'twice.txt'))
        def entry = storage.retrieve('twice.txt').get()

        expect:
        entry.inputStream.text == 'hello world'
        entry.inputStream.text == 'hello world'
    }

    void 'many concurrent downloads share a small memory budget'() {
        given:
        byte[] content = (0..<200).collect { (byte) it } as byte[]
        storage.upload(UploadRequest.fromBytes(content, 'shared.bin'))
        def pool = Executors.newFixedThreadPool(8)

        when:
        def results = pool.invokeAll((1..32).collect {
            { -> storage.retrieve('shared.bin').get().inputStream.withCloseable { it.readAllBytes() } } as Callable<byte[]>
        })*.get()

        then:
        results.every { Arrays.equals(it, content) }

        cleanup:
        pool.shutdownNow()
    }

    void 'an upload above max-object-size fails and stores nothing'() {
        when:
        storage.upload(UploadRequest.fromBytes(new byte[2048], 'too-big.bin'))

        then:
        def e = thrown(ObjectStorageException)
        e.message.contains('too-big.bin') || e.message.toLowerCase().contains('size')
        !storage.exists('too-big.bin')
    }

    void 'an empty object round-trips'() {
        when:
        storage.upload(UploadRequest.fromBytes(new byte[0], 'empty.txt'))

        then:
        storage.retrieve('empty.txt').get().inputStream.readAllBytes().length == 0
    }
}
