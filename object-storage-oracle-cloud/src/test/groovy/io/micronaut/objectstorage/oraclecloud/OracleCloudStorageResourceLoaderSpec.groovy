package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.Region
import com.oracle.bmc.auth.RegionProvider
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class OracleCloudStorageResourceLoaderSpec extends Specification {

    void 'it resolves oracle cloud resources for the active region'() {
        given:
        def configuration = new OracleCloudStorageConfiguration('public-images')
        configuration.bucket = 'public-images-bucket'
        configuration.namespace = 'cliponaut'
        def loader = new OracleCloudStorageResourceLoader(
            [configuration],
            ['public-images': new TestObjectStorageOperations(['avatars/logo.txt': 'oci-logo'])],
            Stub(RegionProvider) {
                getRegion() >> Region.US_ASHBURN_1
            }
        )

        expect:
        loader.supportsPrefix('os:us-ashburn-1:cliponaut://public-images-bucket/avatars/logo.txt')
        loader.getResourceAsStream('os:us-ashburn-1:cliponaut://public-images-bucket/avatars/logo.txt').get().text == 'oci-logo'
    }

    void 'it ignores oracle cloud resources for other regions'() {
        given:
        def configuration = new OracleCloudStorageConfiguration('public-images')
        configuration.bucket = 'public-images-bucket'
        configuration.namespace = 'cliponaut'
        def loader = new OracleCloudStorageResourceLoader(
            [configuration],
            ['public-images': new TestObjectStorageOperations(['avatars/logo.txt': 'oci-logo'])],
            Stub(RegionProvider) {
                getRegion() >> Region.US_ASHBURN_1
            }
        )

        expect:
        !loader.supportsPrefix('os:eu-frankfurt-1:cliponaut://public-images-bucket/avatars/logo.txt')
        loader.getResourceAsStream('os:eu-frankfurt-1:cliponaut://public-images-bucket/avatars/logo.txt').empty
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
