package io.micronaut.objectstorage.googlecloud

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GoogleCloudStorageResourceLoaderSpec extends Specification {

    void 'it resolves gs resources for configured buckets'() {
        given:
        def configuration = new GoogleCloudStorageConfiguration('public-images')
        configuration.bucket = 'public-images-bucket'
        def loader = new GoogleCloudStorageResourceLoader(
            [configuration],
            ['public-images': new TestObjectStorageOperations(['avatars/logo.txt': 'gcp-logo'])]
        )

        expect:
        loader.supportsPrefix('gs://public-images-bucket/avatars/logo.txt')
        loader.getResourceAsStream('gs://public-images-bucket/avatars/logo.txt').get().text == 'gcp-logo'
        loader.forBase('gs://public-images-bucket/avatars/').getResourceAsStream('logo.txt').get().text == 'gcp-logo'
    }

    private static final class TestObjectStorageOperations implements ObjectStorageOperations<Object, Object, Object> {
        private final Map<String, byte[]> contents

        private TestObjectStorageOperations(Map<String, String> contents) {
            this.contents = contents.collectEntries { key, value -> [(key): value.getBytes(StandardCharsets.UTF_8)] }
        }

        @Override
        UploadResponse<Object> upload(io.micronaut.objectstorage.request.UploadRequest request) { throw new UnsupportedOperationException() }

        @Override
        UploadResponse<Object> upload(io.micronaut.objectstorage.request.UploadRequest request, java.util.function.Consumer<Object> requestConsumer) { throw new UnsupportedOperationException() }

        @Override
        Optional<ObjectStorageEntry<byte[]>> retrieve(String key) {
            return Optional.ofNullable(contents.get(key)).map(bytes -> [
                getKey        : { -> key },
                getInputStream: { -> new ByteArrayInputStream(bytes) },
                getNativeEntry: { -> bytes }
            ] as ObjectStorageEntry<byte[]>)
        }

        @Override
        Object delete(String key) { throw new UnsupportedOperationException() }

        @Override
        boolean exists(String key) { contents.containsKey(key) }
    }
}
