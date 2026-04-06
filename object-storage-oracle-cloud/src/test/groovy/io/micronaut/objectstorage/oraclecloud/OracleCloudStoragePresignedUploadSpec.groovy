package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.response.PresignedUpload
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class OracleCloudStoragePresignedUploadSpec extends Specification {

    void "it creates a portable presigned upload for oracle cloud storage"() {
        given:
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> "profile-pictures"
            getNamespace() >> "namespace"
        }
        RegionProvider regionProvider = Stub()
        CreatePreauthenticatedRequestRequest capturedRequest = null
        ObjectStorage client = Mock() {
            1 * createPreauthenticatedRequest(_ as CreatePreauthenticatedRequestRequest) >> { CreatePreauthenticatedRequestRequest request ->
                capturedRequest = request
                return CreatePreauthenticatedRequestResponse.builder()
                    .__httpStatusCode__(200)
                    .preauthenticatedRequest(PreauthenticatedRequest.builder()
                        .accessUri("/p/secret/n/namespace/b/profile-pictures/o/avatars/alice.png")
                        .build())
                    .build()
            }
            1 * getEndpoint() >> "https://objectstorage.eu-frankfurt-1.oraclecloud.com"
        }
        OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, regionProvider)
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5))
        request.contentType = "image/png"
        request.contentLength = 128
        request.metadata = [owner: "alice"]
        Instant before = Instant.now()

        when:
        PresignedUpload response = operations.createPresignedUpload(request).get()
        Instant after = Instant.now()

        then:
        capturedRequest.namespaceName == "namespace"
        capturedRequest.bucketName == "profile-pictures"
        capturedRequest.createPreauthenticatedRequestDetails.objectName == "avatars/alice.png"
        capturedRequest.createPreauthenticatedRequestDetails.accessType == CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite
        capturedRequest.createPreauthenticatedRequestDetails.name == "micronaut-presigned-upload-avatars-alice.png"
        capturedRequest.createPreauthenticatedRequestDetails.timeExpires.toInstant() >= before.plus(Duration.ofMinutes(5)).minusMillis(1)
        capturedRequest.createPreauthenticatedRequestDetails.timeExpires.toInstant() <= after.plus(Duration.ofMinutes(5)).plusMillis(1)
        response.method == "PUT"
        response.uri == URI.create("https://objectstorage.eu-frankfurt-1.oraclecloud.com/p/secret/n/namespace/b/profile-pictures/o/avatars/alice.png")
        response.headers["Content-Type"] == ["image/png"]
        response.headers["Content-Length"] == ["128"]
        response.headers["opc-meta-owner"] == ["alice"]
        response.expiration >= before.plus(Duration.ofMinutes(5))
        response.expiration <= after.plus(Duration.ofMinutes(5))
    }

    void "it wraps OCI preauthenticated request failures as object storage exceptions"() {
        given:
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> "profile-pictures"
            getNamespace() >> "namespace"
        }
        RegionProvider regionProvider = Stub()
        ObjectStorage client = Mock() {
            1 * createPreauthenticatedRequest(_ as CreatePreauthenticatedRequestRequest) >> {
                throw BmcException.createClientSide("boom", null, null, null)
            }
        }
        OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, regionProvider)

        when:
        operations.createPresignedUpload(new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5)))

        then:
        ObjectStorageException e = thrown()
        e.message.contains("pre-signed upload request")
    }
}
