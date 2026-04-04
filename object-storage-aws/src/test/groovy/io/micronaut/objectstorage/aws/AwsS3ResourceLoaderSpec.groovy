package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class AwsS3ResourceLoaderSpec extends Specification {

    void 'it resolves s3 resources for configured buckets and rebases relative lookups'() {
        given:
        def configuration = new AwsS3Configuration('public-images')
        configuration.bucket = 'public-images-bucket'
        def loader = new AwsS3ResourceLoader(
            [configuration],
            ['public-images': new TestObjectStorageOperations(['avatars/logo.txt': 'aws-logo'])]
        )

        expect:
        loader.supportsPrefix('s3://public-images-bucket/avatars/logo.txt')
        loader.getResourceAsStream('s3://public-images-bucket/avatars/logo.txt').get().text == 'aws-logo'
        loader.forBase('s3://public-images-bucket/avatars/').getResourceAsStream('logo.txt').get().text == 'aws-logo'
    }

    void 'it fails deterministically when multiple storages match the same bucket'() {
        given:
        def first = new AwsS3Configuration('first')
        first.bucket = 'shared-bucket'
        def second = new AwsS3Configuration('second')
        second.bucket = 'shared-bucket'
        def loader = new AwsS3ResourceLoader(
            [first, second],
            [
                (first.name) : new TestObjectStorageOperations(['logo.txt': 'a']),
                (second.name): new TestObjectStorageOperations(['logo.txt': 'b'])
            ]
        )

        when:
        loader.getResourceAsStream('s3://shared-bucket/logo.txt')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Multiple configured AWS object storages')
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
