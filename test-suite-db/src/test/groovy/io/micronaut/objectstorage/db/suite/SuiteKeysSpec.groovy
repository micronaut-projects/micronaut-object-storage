package io.micronaut.objectstorage.db.suite

import io.micronaut.objectstorage.db.DbObjectMetadataOperations
import io.micronaut.objectstorage.db.DbStorageOperations
import io.micronaut.objectstorage.request.BytesUploadRequest
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest(transactional = false)
class SuiteKeysSpec extends Specification implements TestPropertyProvider {

    @Inject
    DbStorageOperations storage

    @Inject
    DbObjectMetadataOperations metadataOperations

    @Override
    Map<String, String> getProperties() {
        SuiteProperties.storage('keys', ['chunk-size': '16'])
    }

    def cleanup() {
        storage.listObjects().each { storage.delete(it) }
    }

    void 'keys are case sensitive and listed in binary order'() {
        given:
        List<String> keys = ['b', 'B', 'a', 'A', 'a/b', 'a-b', 'Z', 'z', 'é', 'ñ', '日本']
        keys.each { storage.upload(UploadRequest.fromBytes(it.getBytes(StandardCharsets.UTF_8), it)) }

        expect:
        keys.every { storage.retrieve(it).get().inputStream.getText('UTF-8') == it }
        storage.listObjects() as List == keys.sort(false) { a, b -> compareUtf8(a, b) }

        and: 'pages follow the same order'
        pages(3) == keys.sort(false) { a, b -> compareUtf8(a, b) }
    }

    void 'prefixes with LIKE wildcards and the escape character match literally'() {
        given:
        ['100%/a', '100x/b', 'a_b/c', 'axb/d', 'x!y/e', 'x!!y/f'].each { storage.upload(UploadRequest.fromBytes('x'.bytes, it)) }

        expect:
        list('100%') == ['100%/a']
        list('a_') == ['a_b/c']
        list('x!') == ['x!!y/f', 'x!y/e']
        list('x!y') == ['x!y/e']
    }

    void 'long keys and large metadata round-trip'() {
        given:
        String key = 'k/' + ('x' * 1000)
        Map<String, String> metadata = (1..50).collectEntries { ["key-$it".toString(), 'é' * 100] }

        when:
        storage.upload(new BytesUploadRequest('content'.bytes, 'text/plain', key, metadata))

        then:
        storage.retrieve(key).get().metadata == metadata
        metadataOperations.retrieve(key).get().metadata() == metadata
    }

    void 'binary content of every byte value round-trips across windows'() {
        given:
        byte[] content = (0..<1000).collect { (byte) (it % 256) } as byte[]

        when:
        storage.upload(UploadRequest.fromBytes(content, 'binary.bin'))

        then:
        storage.retrieve('binary.bin').get().inputStream.readAllBytes() == content
    }

    private List<String> list(String prefix) {
        storage.listObjects(new ListObjectsRequest(100, prefix, null)).keys as List
    }

    private List<String> pages(int size) {
        List<String> all = []
        String token = null
        do {
            def response = storage.listObjects(new ListObjectsRequest(size, null, token))
            all.addAll(response.keys)
            token = response.continuationToken.orElse(null)
        } while (token != null)
        all
    }

    private static int compareUtf8(String a, String b) {
        Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8))
    }
}
