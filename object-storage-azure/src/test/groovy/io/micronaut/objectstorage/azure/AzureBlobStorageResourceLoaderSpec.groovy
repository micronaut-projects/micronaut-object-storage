package io.micronaut.objectstorage.azure

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class AzureBlobStorageResourceLoaderSpec extends Specification {

    void 'it resolves azure blob resources for configured account and container'() {
        given:
        def configuration = new AzureBlobStorageConfiguration('public-images')
        configuration.endpoint = 'https://cliponaut.blob.core.windows.net'
        configuration.container = 'public-images'
        def loader = new AzureBlobStorageResourceLoader(
            [configuration],
            ['public-images': new TestObjectStorageOperations(['avatars/logo.txt': 'azure-logo'])]
        )

        expect:
        loader.supportsPrefix('azb:cliponaut://public-images/avatars/logo.txt')
        loader.getResourceAsStream('azb:cliponaut://public-images/avatars/logo.txt').get().text == 'azure-logo'
        loader.forBase('azb:cliponaut://public-images/avatars/').getResourceAsStream('logo.txt').get().text == 'azure-logo'
    }

    void 'it extracts azurite account names from the endpoint path'() {
        given:
        def configuration = new AzureBlobStorageConfiguration('public-images')
        configuration.endpoint = 'http://127.0.0.1:10000/devstoreaccount1'
        configuration.container = 'public-images'
        def loader = new AzureBlobStorageResourceLoader(
            [configuration],
            ['public-images': new TestObjectStorageOperations(['avatars/logo.txt': 'azure-logo'])]
        )

        expect:
        loader.getResourceAsStream('azb:devstoreaccount1://public-images/avatars/logo.txt').get().text == 'azure-logo'
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
