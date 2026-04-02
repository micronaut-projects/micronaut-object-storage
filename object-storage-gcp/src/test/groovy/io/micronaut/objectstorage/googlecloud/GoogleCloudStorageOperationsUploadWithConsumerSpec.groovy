package io.micronaut.objectstorage.googlecloud


import com.google.cloud.storage.*
import groovy.transform.AutoImplement
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Shared
import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.Optional

@Property(name = "micronaut.object-storage.gcp.default.bucket", value = "profile-pictures-bucket")
@Property(name = "spec.name", value = SPEC_NAME)
@MicronautTest
class GoogleCloudStorageOperationsUploadWithConsumerSpec extends Specification {

    private static final String SPEC_NAME = "GoogleCloudStorageOperationsUploadWithConsumerSpec"

    @Inject
    ObjectStorageOperations<BlobInfo.Builder, Blob, ?> objectStorage

    @Inject
    StorageReplacement storageReplacement

    @Shared
    static Blob blobMock

    void "consumer accept is invoked"() {
        given:
        blobMock = Mock(Blob)
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        UploadRequest uploadRequest = UploadRequest.fromPath(path)

        when:
        objectStorage.upload(uploadRequest, builder -> {
            builder.metadata = [project: "micronaut-object-storage"]
        });

        then:
        storageReplacement.blobInfo
        [project: "micronaut-object-storage"] == storageReplacement.blobInfo.metadata
    }

    void "uploads use the google cloud stream API"() {
        given:
        byte[] bytes = "stream-body".bytes
        storageReplacement.reset()
        UploadRequest uploadRequest = new StubUploadRequest("stream.txt", bytes)

        when:
        objectStorage.upload(uploadRequest)

        then:
        storageReplacement.inputStreamCreateInvoked
        !storageReplacement.byteArrayCreateInvoked
        storageReplacement.blobInfo
        storageReplacement.blobInfo.contentType == "text/plain"
        storageReplacement.streamedBytes == bytes
    }

    static Blob blobMock() {
        return blobMock
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(Storage)
    @Singleton
    @AutoImplement
    static class StorageReplacement implements Storage {

        BlobInfo blobInfo
        boolean byteArrayCreateInvoked
        boolean inputStreamCreateInvoked
        byte[] streamedBytes

        @Override
        Blob create(BlobInfo blobInfo, byte[] content, BlobTargetOption... options) {
            this.blobInfo = blobInfo
            this.byteArrayCreateInvoked = true
            return blobMock()
        }

        @Override
        Blob create(BlobInfo blobInfo, InputStream content, BlobWriteOption... options) {
            this.blobInfo = blobInfo
            this.inputStreamCreateInvoked = true
            this.streamedBytes = content.bytes
            return blobMock()
        }

        void reset() {
            blobInfo = null
            byteArrayCreateInvoked = false
            inputStreamCreateInvoked = false
            streamedBytes = null
        }
    }

    private static final class StubUploadRequest implements UploadRequest {
        private final String key
        private final byte[] bytes

        StubUploadRequest(String key, byte[] bytes) {
            this.key = key
            this.bytes = bytes
        }

        @Override
        Optional<String> getContentType() {
            return Optional.of("text/plain")
        }

        @Override
        String getKey() {
            return key
        }

        @Override
        Optional<Long> getContentSize() {
            return Optional.of(bytes.length as long)
        }

        @Override
        InputStream getInputStream() {
            return new ByteArrayInputStream(bytes)
        }
    }
}
