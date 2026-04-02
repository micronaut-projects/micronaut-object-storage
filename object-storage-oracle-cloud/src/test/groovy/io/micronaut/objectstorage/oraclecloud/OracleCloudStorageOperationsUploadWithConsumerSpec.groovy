package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import com.oracle.bmc.objectstorage.transfer.UploadManager
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

import java.util.function.Supplier

class OracleCloudStorageOperationsUploadWithConsumerSpec extends Specification {

    void "known-length uploads use UploadManager and preserve PutObjectResponse"() {
        given:
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> 'bucket'
            getNamespace() >> 'namespace'
        }
        ObjectStorage client = Mock()
        RegionProvider regionProvider = Stub()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, regionProvider, { uploadManager } as Supplier<UploadManager>)
        UploadRequest uploadRequest = UploadRequest.fromBytes('micronaut'.bytes, 'guide.txt', 'text/plain')

        when:
        def response = operations.upload(uploadRequest) { builder ->
            builder.opcMeta([project: 'micronaut-object-storage'])
        }

        then:
        1 * uploadManager.upload(_ as UploadManager.UploadRequest) >> { UploadManager.UploadRequest managerRequest ->
            PutObjectRequest putObjectRequest = extractPutObjectRequest(managerRequest)
            assert putObjectRequest.bucketName == 'bucket'
            assert putObjectRequest.namespaceName == 'namespace'
            assert putObjectRequest.objectName == 'guide.txt'
            assert putObjectRequest.contentLength == 'micronaut'.bytes.length as long
            assert putObjectRequest.contentType == 'text/plain'
            assert putObjectRequest.opcMeta == [project: 'micronaut-object-storage']
            assert putObjectRequest.putObjectBody != null
            new UploadManager.UploadResponse(
                'etag',
                'content-md5',
                'multipart-md5',
                'opc-request-id',
                'opc-client-request-id',
                'content-crc32c',
                'content-sha256',
                'content-sha384',
                'multipart-sha256',
                'multipart-sha384'
            )
        }
        0 * client._

        and:
        response.key == 'guide.txt'
        response.eTag == 'etag'
        response.nativeResponse instanceof PutObjectResponse
        response.nativeResponse.eTag == 'etag'
        response.nativeResponse.opcRequestId == 'opc-request-id'
        response.nativeResponse.opcClientRequestId == 'opc-client-request-id'
        response.nativeResponse.opcContentMd5 == 'content-md5'
        response.nativeResponse.opcContentCrc32c == 'content-crc32c'
        response.nativeResponse.opcContentSha256 == 'content-sha256'
        response.nativeResponse.opcContentSha384 == 'content-sha384'
    }

    void "unknown-length uploads fall back to ObjectStorage client"() {
        given:
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> 'bucket'
            getNamespace() >> 'namespace'
        }
        ObjectStorage client = Mock()
        RegionProvider regionProvider = Stub()
        UploadManager uploadManager = Mock()
        OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, regionProvider, { uploadManager } as Supplier<UploadManager>)
        UploadRequest uploadRequest = Stub() {
            getKey() >> 'streaming.txt'
            getContentSize() >> Optional.empty()
            getContentType() >> Optional.of('text/plain')
            getInputStream() >> new ByteArrayInputStream('streaming'.bytes)
        }

        when:
        def response = operations.upload(uploadRequest)

        then:
        1 * client.putObject({ PutObjectRequest request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.objectName == 'streaming.txt' &&
                request.contentLength == null &&
                request.contentType == 'text/plain' &&
                request.putObjectBody != null
        }) >> PutObjectResponse.builder()
            .eTag('etag')
            .opcRequestId('opc-request-id')
            .build()
        0 * uploadManager._

        and:
        response.key == 'streaming.txt'
        response.eTag == 'etag'
        response.nativeResponse.opcRequestId == 'opc-request-id'
    }

    private static PutObjectRequest extractPutObjectRequest(UploadManager.UploadRequest uploadRequest) {
        def field = UploadManager.UploadRequest.getDeclaredField('putObjectRequest')
        field.accessible = true
        return (PutObjectRequest) field.get(uploadRequest)
    }
}
