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

import java.nio.file.Path

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

    static Blob blobMock() {
        return blobMock
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(Storage)
    @Singleton
    @AutoImplement
    static class StorageReplacement implements Storage {

        BlobInfo blobInfo

        @Override
        Blob create(BlobInfo blobInfo, byte[] content, BlobTargetOption... options) {
            this.blobInfo = blobInfo
            return blobMock()
        }

    }
}
