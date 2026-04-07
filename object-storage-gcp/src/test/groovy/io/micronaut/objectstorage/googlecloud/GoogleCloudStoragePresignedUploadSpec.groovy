package io.micronaut.objectstorage.googlecloud

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import io.micronaut.objectstorage.response.PresignedUpload
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class GoogleCloudStoragePresignedUploadSpec extends Specification {

    void "it creates a portable presigned upload for google cloud storage"() {
        given:
        GoogleCloudStorageConfiguration configuration = new GoogleCloudStorageConfiguration("default")
        configuration.bucket = "profile-pictures"
        BlobInfo capturedBlobInfo = null
        long capturedDuration = -1
        TimeUnit capturedTimeUnit = null
        Storage storage = Mock() {
            1 * signUrl(_ as BlobInfo, _ as Long, _ as TimeUnit, _ as Storage.SignUrlOption[]) >> {
                BlobInfo blobInfo, Long duration, TimeUnit timeUnit, Storage.SignUrlOption[] ignored ->
                    capturedBlobInfo = blobInfo
                    capturedDuration = duration
                    capturedTimeUnit = timeUnit
                    return new URL("https://storage.googleapis.com/profile-pictures/avatars/alice.png")
            }
        }
        GoogleCloudStorageOperations operations = new GoogleCloudStorageOperations(
            configuration,
            storage
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
        capturedBlobInfo.blobId == BlobId.of("profile-pictures", "avatars/alice.png")
        capturedBlobInfo.contentType == "image/png"
        capturedBlobInfo.metadata == [owner: "alice"]
        capturedDuration == Duration.ofMinutes(5).toMillis()
        capturedTimeUnit == TimeUnit.MILLISECONDS
        response.method == "PUT"
        response.uri == URI.create("https://storage.googleapis.com/profile-pictures/avatars/alice.png")
        response.headers["Content-Type"] == ["image/png"]
        response.headers["Content-Length"] == ["128"]
        response.headers["x-goog-meta-owner"] == ["alice"]
        response.expiration >= before.plus(Duration.ofMinutes(5))
        response.expiration <= after.plus(Duration.ofMinutes(5))
    }

    void "it wraps signing failures as object storage exceptions"() {
        given:
        GoogleCloudStorageConfiguration configuration = new GoogleCloudStorageConfiguration("default")
        configuration.bucket = "profile-pictures"
        GoogleCloudStorageOperations operations = new GoogleCloudStorageOperations(
            configuration,
            Mock(Storage) {
                1 * signUrl(_ as BlobInfo, _ as Long, _ as TimeUnit, _ as Storage.SignUrlOption[]) >> {
                    throw new IllegalStateException("signer unavailable")
                }
            }
        )

        when:
        operations.createPresignedUpload(new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5)))

        then:
        ObjectStorageException e = thrown()
        e.message.contains("pre-signed upload request")
    }
}
