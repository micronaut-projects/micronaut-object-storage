package io.micronaut.objectstorage.azure

import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import io.micronaut.objectstorage.response.PresignedUpload
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class AzureBlobStoragePresignedUploadSpec extends Specification {

    void "it creates a portable presigned upload for azure blob storage"() {
        given:
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential("account", "ZmFrZS1rZXk=")
        AzureBlobStorageOperations operations = new AzureBlobStorageOperations(
            new BlobContainerClientBuilder()
                .endpoint("https://account.blob.core.windows.net")
                .containerName("profile-pictures")
                .credential(credential)
                .buildClient(),
            credential
        )
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5))
        request.contentType = "image/png"
        request.contentLength = 128
        request.metadata = [owner: "alice"]
        Instant before = Instant.now()

        when:
        PresignedUpload response = operations.createPresignedUpload(request).get()
        Instant after = Instant.now()

        then:
        response.method == "PUT"
        response.uri.toString().startsWith("https://account.blob.core.windows.net/profile-pictures/")
        response.uri.toString().contains("avatars%2Falice.png")
        response.uri.query.contains("sig=")
        response.headers["x-ms-blob-type"] == ["BlockBlob"]
        response.headers["x-ms-blob-content-type"] == ["image/png"]
        response.headers["Content-Length"] == ["128"]
        response.headers["x-ms-meta-owner"] == ["alice"]
        response.expiration >= before.plus(Duration.ofMinutes(5))
        response.expiration <= after.plus(Duration.ofMinutes(5))
    }

    void "it fails fast when shared key credentials are unavailable"() {
        given:
        AzureBlobStorageOperations operations = new AzureBlobStorageOperations(
            new BlobContainerClientBuilder()
                .endpoint("https://account.blob.core.windows.net")
                .containerName("profile-pictures")
                .setAnonymousAccess()
                .buildClient(),
            null
        )

        when:
        operations.createPresignedUpload(new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5)))

        then:
        ObjectStorageException e = thrown()
        e.message.contains("StorageSharedKeyCredential")
    }
}
