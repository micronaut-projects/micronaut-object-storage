package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.MultipartUpload
import com.oracle.bmc.objectstorage.model.MultipartUploadPartSummary
import com.oracle.bmc.objectstorage.requests.AbortMultipartUploadRequest as OciAbortMultipartUploadRequest
import com.oracle.bmc.objectstorage.requests.CommitMultipartUploadRequest as OciCommitMultipartUploadRequest
import com.oracle.bmc.objectstorage.requests.CreateMultipartUploadRequest as OciCreateMultipartUploadRequest
import com.oracle.bmc.objectstorage.requests.ListMultipartUploadPartsRequest as OciListMultipartUploadPartsRequest
import com.oracle.bmc.objectstorage.requests.UploadPartRequest as OciUploadPartRequest
import com.oracle.bmc.objectstorage.responses.CommitMultipartUploadResponse as OciCommitMultipartUploadResponse
import com.oracle.bmc.objectstorage.responses.CreateMultipartUploadResponse as OciCreateMultipartUploadResponse
import com.oracle.bmc.objectstorage.responses.ListMultipartUploadPartsResponse as OciListMultipartUploadPartsResponse
import com.oracle.bmc.objectstorage.responses.UploadPartResponse as OciUploadPartResponse
import com.oracle.bmc.objectstorage.transfer.UploadManager
import io.micronaut.objectstorage.multipart.MultipartPart
import io.micronaut.objectstorage.multipart.MultipartUploadHandle
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.util.function.Supplier
import java.util.Optional

class OracleCloudMultipartOperationsSpec extends Specification {

    void "create multipart upload maps portable request to OCI request"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        def response = operations.createMultipartUpload(
            new CreateMultipartUploadRequest('videos/demo.mp4', 'video/mp4', [owner: 'micronaut'])
        )

        then:
        1 * client.createMultipartUpload({ OciCreateMultipartUploadRequest request ->
            request.namespaceName == 'namespace' &&
                request.bucketName == 'bucket' &&
                request.createMultipartUploadDetails.object == 'videos/demo.mp4' &&
                request.createMultipartUploadDetails.contentType == 'video/mp4' &&
                request.createMultipartUploadDetails.metadata == [owner: 'micronaut']
        }) >> OciCreateMultipartUploadResponse.builder()
            .multipartUpload(MultipartUpload.builder()
                .object('videos/demo.mp4')
                .uploadId('upload-1')
                .build())
            .build()
        0 * uploadManager._

        and:
        response.upload.key == 'videos/demo.mp4'
        response.upload.uploadId == 'upload-1'
    }

    void "upload part streams declared-size request to OCI"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)
        UploadRequest uploadRequest = UploadRequest.fromBytes([1, 2, 3, 4] as byte[], 'videos/demo.mp4', 'video/mp4')

        when:
        def response = operations.uploadPart(new UploadPartRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1'), 1, uploadRequest))

        then:
        1 * client.uploadPart({ OciUploadPartRequest request ->
            request.namespaceName == 'namespace' &&
                request.bucketName == 'bucket' &&
                request.objectName == 'videos/demo.mp4' &&
                request.uploadId == 'upload-1' &&
                request.uploadPartNum == 1 &&
                request.contentLength == 4L &&
                request.uploadPartBody != null
        }) >> OciUploadPartResponse.builder()
            .eTag('etag-1')
            .opcContentSha256('sha256-1')
            .build()
        0 * uploadManager._

        and:
        response.part.partNumber == 1
        response.part.size == 4L
        response.part.ETag == 'etag-1'
        response.part.getChecksum().get() == 'sha256-1'
    }

    void "upload part rejects streaming requests without content size"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)
        UploadRequest uploadRequest = Stub() {
            getKey() >> 'videos/demo.mp4'
            getContentSize() >> Optional.empty()
            getContentType() >> Optional.of('video/mp4')
            getInputStream() >> new ByteArrayInputStream([1, 2, 3, 4] as byte[])
        }

        when:
        operations.uploadPart(new UploadPartRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1'), 1, uploadRequest))

        then:
        ObjectStorageException e = thrown()
        e.message == 'Multipart uploads require UploadRequest#getContentSize() for streaming requests'
        0 * client._
        0 * uploadManager._
    }

    void "list parts maps OCI response to portable parts and continuation token"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        def response = operations.listParts(new ListMultipartPartsRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1'), 2, 'next-page'))

        then:
        1 * client.listMultipartUploadParts({ OciListMultipartUploadPartsRequest request ->
            request.namespaceName == 'namespace' &&
                request.bucketName == 'bucket' &&
                request.objectName == 'videos/demo.mp4' &&
                request.uploadId == 'upload-1' &&
                request.limit == 2 &&
                request.page == 'next-page'
        }) >> OciListMultipartUploadPartsResponse.builder()
            .opcNextPage('next-page-2')
            .items([
                MultipartUploadPartSummary.builder()
                    .partNumber(1)
                    .etag('etag-1')
                    .size(4L)
                    .md5('md5-1')
                    .build()
            ])
            .build()
        0 * uploadManager._

        and:
        response.parts*.partNumber == [1]
        response.parts*.ETag == ['etag-1']
        response.parts*.size == [4L]
        response.parts.first().getChecksum().get() == 'md5-1'
        response.getContinuationToken().get() == 'next-page-2'
    }

    void "list parts maps empty OCI response to empty portable page"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        def response = operations.listParts(new ListMultipartPartsRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1'), 2))

        then:
        1 * client.listMultipartUploadParts(_ as OciListMultipartUploadPartsRequest) >> OciListMultipartUploadPartsResponse.builder().build()
        0 * uploadManager._

        and:
        response.parts.empty
        !response.getContinuationToken().present
    }

    void "complete multipart upload maps portable manifest to OCI commit request"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        def response = operations.completeMultipartUpload(new CompleteMultipartUploadRequest(
            new MultipartUploadHandle('videos/demo.mp4', 'upload-1'),
            [
                new MultipartPart(1, 'etag-1', 4L),
                new MultipartPart(2, 'etag-2', 5L)
            ]
        ))

        then:
        1 * client.commitMultipartUpload({ OciCommitMultipartUploadRequest request ->
            request.namespaceName == 'namespace' &&
                request.bucketName == 'bucket' &&
                request.objectName == 'videos/demo.mp4' &&
                request.uploadId == 'upload-1' &&
                request.commitMultipartUploadDetails.partsToCommit*.partNum == [1, 2] &&
                request.commitMultipartUploadDetails.partsToCommit*.etag == ['etag-1', 'etag-2']
        }) >> OciCommitMultipartUploadResponse.builder()
            .eTag('final-etag')
            .build()
        0 * uploadManager._

        and:
        response.upload.uploadId == 'upload-1'
        response.ETag == 'final-etag'
    }

    void "abort multipart upload maps portable request to OCI request"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        operations.abortMultipartUpload(new AbortMultipartUploadRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1')))

        then:
        1 * client.abortMultipartUpload({ OciAbortMultipartUploadRequest request ->
            request.namespaceName == 'namespace' &&
                request.bucketName == 'bucket' &&
                request.objectName == 'videos/demo.mp4' &&
                request.uploadId == 'upload-1'
        })
        0 * uploadManager._
    }

    void "abort multipart upload treats not found as retry safe"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        operations.abortMultipartUpload(new AbortMultipartUploadRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1')))

        then:
        1 * client.abortMultipartUpload(_ as OciAbortMultipartUploadRequest) >> {
            throw new BmcException(404, 'NotAuthorizedOrNotFound', 'missing', null)
        }
        0 * uploadManager._
        noExceptionThrown()
    }

    void "abort multipart upload wraps unexpected OCI failures"() {
        given:
        ObjectStorage client = Mock()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = operations(client, uploadManager)

        when:
        operations.abortMultipartUpload(new AbortMultipartUploadRequest(new MultipartUploadHandle('videos/demo.mp4', 'upload-1')))

        then:
        1 * client.abortMultipartUpload(_ as OciAbortMultipartUploadRequest) >> {
            throw new BmcException(500, 'InternalError', 'boom', null)
        }
        0 * uploadManager._
        ObjectStorageException e = thrown()
        e.message == 'Error when trying to abort multipart upload [upload-1] in Oracle Cloud Storage'
    }

    private OracleCloudStorageOperations operations(ObjectStorage client, UploadManager uploadManager) {
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> 'bucket'
            getNamespace() >> 'namespace'
        }
        RegionProvider regionProvider = Stub()
        return new OracleCloudStorageOperations(configuration, client, regionProvider, { uploadManager } as Supplier<UploadManager>)
    }
}
