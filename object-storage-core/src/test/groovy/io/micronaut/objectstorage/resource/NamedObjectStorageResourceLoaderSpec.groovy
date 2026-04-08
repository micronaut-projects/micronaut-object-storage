package io.micronaut.objectstorage.resource

import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.DisabledBeanException
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class NamedObjectStorageResourceLoaderSpec extends Specification {

    void 'it resolves named storage resources and rebases relative lookups'() {
        given:
        def operations = new TestObjectStorageOperations([
            'avatars/logo.txt': 'logo-bytes',
            'avatars/logo:v2.txt': 'logo-v2-bytes'
        ])
        def loader = new NamedObjectStorageResourceLoader('public-images', operations)

        expect:
        loader.supportsPrefix('public-images://avatars/logo.txt')
        loader.getResourceAsStream('public-images://avatars/logo.txt').get().text == 'logo-bytes'
        loader.getResource('public-images://avatars/logo.txt').get().text == 'logo-bytes'
        loader.getResources('public-images://avatars/logo.txt').toList()*.text == ['logo-bytes']

        when:
        def rebased = loader.forBase('public-images://avatars/')

        then:
        rebased.supportsPrefix('logo.txt')
        rebased.getResourceAsStream('logo.txt').get().text == 'logo-bytes'
        rebased.supportsPrefix('logo:v2.txt')
        rebased.getResourceAsStream('logo:v2.txt').get().text == 'logo-v2-bytes'
        rebased.getResourceAsStream('nested/../logo.txt').get().text == 'logo-bytes'
    }

    void 'it rejects relative lookups that escape the rebased prefix'() {
        given:
        def operations = new TestObjectStorageOperations([
            'avatars/logo.txt': 'logo-bytes',
            'secret.txt': 'secret-bytes'
        ])
        def rebased = new NamedObjectStorageResourceLoader('public-images', operations)
            .forBase('public-images://avatars/')

        expect:
        rebased.supportsPrefix('../secret.txt')

        when:
        rebased.getResourceAsStream('../secret.txt')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('escapes the configured base path')
    }

    void 'it returns empty for missing objects'() {
        given:
        def loader = new NamedObjectStorageResourceLoader('public-images', new TestObjectStorageOperations([:]))

        expect:
        loader.getResourceAsStream('public-images://missing.txt').empty
        loader.getResource('public-images://missing.txt').empty
        loader.getResources('public-images://missing.txt').toList().empty
    }

    void 'it rejects malformed named storage uris'() {
        given:
        def loader = new NamedObjectStorageResourceLoader('public-images', new TestObjectStorageOperations([:]))

        expect:
        loader.supportsPrefix('public-images:/avatars/logo.txt')

        when:
        loader.getResourceAsStream('public-images://')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid object storage URI')
    }

    void 'it rejects malformed named storage uris after supportsPrefix'() {
        given:
        def loader = new NamedObjectStorageResourceLoader('public-images', new TestObjectStorageOperations([:]))

        when:
        loader.getResourceAsStream('public-images:/avatars/logo.txt')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid object storage URI')
    }

    void 'it rejects reserved named loader prefixes'() {
        given:
        def factory = new ObjectStorageNamedResourceLoaderFactory()
        def configuration = Stub(io.micronaut.objectstorage.configuration.ObjectStorageConfiguration) {
            getName() >> 'string'
        }

        when:
        factory.namedResourceLoader(configuration, Stub(BeanContext))

        then:
        thrown(DisabledBeanException)
    }

    private static final class TestObjectStorageOperations implements ObjectStorageOperations<Object, Object, Object> {
        private final Map<String, byte[]> contents

        private TestObjectStorageOperations(Map<String, String> contents) {
            this.contents = contents.collectEntries { key, value -> [(key): value.getBytes(StandardCharsets.UTF_8)] }
        }

        @Override
        UploadResponse<Object> upload(io.micronaut.objectstorage.request.UploadRequest request) {
            throw new UnsupportedOperationException()
        }

        @Override
        UploadResponse<Object> upload(io.micronaut.objectstorage.request.UploadRequest request, java.util.function.Consumer<Object> requestConsumer) {
            throw new UnsupportedOperationException()
        }

        @Override
        Optional<TestObjectStorageEntry> retrieve(String key) {
            return Optional.ofNullable(contents.get(key)).map(bytes -> new TestObjectStorageEntry(key, bytes))
        }

        @Override
        Object delete(String key) {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean exists(String key) {
            return contents.containsKey(key)
        }
    }

    private static final class TestObjectStorageEntry implements ObjectStorageEntry<byte[]> {
        private final String key
        private final byte[] bytes

        private TestObjectStorageEntry(String key, byte[] bytes) {
            this.key = key
            this.bytes = bytes
        }

        @Override
        String getKey() {
            return key
        }

        @Override
        InputStream getInputStream() {
            return new ByteArrayInputStream(bytes)
        }

        @Override
        byte[] getNativeEntry() {
            return bytes
        }
    }
}
