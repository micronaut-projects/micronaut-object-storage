package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.Region
import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.requests.*
import com.oracle.bmc.objectstorage.responses.*
import groovy.transform.AutoImplement
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

@Property(name = "micronaut.object-storage.oracle-cloud.default.bucket", value = "profile-pictures-bucket")
@Property(name = "spec.name", value = SPEC_NAME)
@MicronautTest
class OracleCloudStorageOperationsUploadWithConsumerSpec extends Specification {

    private static final String SPEC_NAME = "OracleCloudStorageOperationsUploadWithConsumerSpec"

    @Inject
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, ?> objectStorage

    @Inject
    ObjectStorageReplacement objectStorageReplacement

    void "consumer accept is invoked"() {
        given:
        Path tempFilePath = Files.createTempFile('test-file', 'txt')
        tempFilePath.toFile().text = 'micronaut'
        UploadRequest uploadRequest = UploadRequest.fromPath(tempFilePath)

        when:
        objectStorage.upload(uploadRequest, builder -> {
                builder.opcMeta([project: "micronaut-object-storage"])
        });

        then:
        objectStorageReplacement.request
        [project: "micronaut-object-storage"] == objectStorageReplacement.request.opcMeta
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(ObjectStorage)
    @Singleton
    @AutoImplement
    static class ObjectStorageReplacement implements ObjectStorage {

        PutObjectRequest request

        @Override
        PutObjectResponse putObject(PutObjectRequest request) {
            this.request = request
            return PutObjectResponse.builder().eTag("etag").build()
        }

    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class TestRegionProvider implements RegionProvider {

        @Override
        Region getRegion() {
            return Region.EU_MARSEILLE_1
        }
    }
}
