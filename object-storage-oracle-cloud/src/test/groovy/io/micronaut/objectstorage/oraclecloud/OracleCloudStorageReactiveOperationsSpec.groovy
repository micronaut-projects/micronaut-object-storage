package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.Region
import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorageAsyncClient
import com.oracle.bmc.objectstorage.model.ListObjects
import com.oracle.bmc.objectstorage.model.ObjectSummary
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest as OciListObjectsRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.responses.AsyncHandler
import com.oracle.bmc.objectstorage.responses.CopyObjectResponse
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse as OciListObjectsResponse
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.util.concurrent.CompletionException
import java.util.concurrent.Future

class OracleCloudStorageReactiveOperationsSpec extends Specification {

    void "upload delegates to the OCI reactor client"() {
        given:
        OracleCloudStorageConfiguration configuration = configuration()
        ObjectStorageAsyncClient asyncClient = Mock()
        RegionProvider regionProvider = Stub(RegionProvider)
        OracleCloudStorageReactiveOperations operations =
            new OracleCloudStorageReactiveOperations(configuration, asyncClient, regionProvider)
        UploadRequest request = UploadRequest.fromBytes("hello".bytes, "dir/file.txt")
        request.metadata = [foo: "bar"]
        request.contentType = "text/plain"
        PutObjectRequest capturedRequest = null

        asyncClient.putObject(_ as PutObjectRequest, _ as AsyncHandler<PutObjectRequest, PutObjectResponse>) >> {
                PutObjectRequest putObjectRequest,
                AsyncHandler<PutObjectRequest, PutObjectResponse> handler ->
            capturedRequest = putObjectRequest
            handler.onSuccess(putObjectRequest, PutObjectResponse.builder()
                .eTag("etag")
                .build())
            Mock(Future)
        }

        when:
        UploadResponse<PutObjectResponse> response = Mono.from(operations.upload(request)).block()

        then:
        response.key == "dir/file.txt"
        response.ETag == "etag"
        capturedRequest.namespaceName == "namespace"
        capturedRequest.bucketName == "bucket"
        capturedRequest.objectName == "dir/file.txt"
        capturedRequest.contentType == "text/plain"
        capturedRequest.opcMeta == [foo: "bar"]
        capturedRequest.putObjectBody != null
    }

    void "listObjects aggregates OCI pagination tokens"() {
        given:
        OracleCloudStorageConfiguration configuration = configuration()
        ObjectStorageAsyncClient asyncClient = Mock()
        RegionProvider regionProvider = Stub(RegionProvider)
        OracleCloudStorageReactiveOperations operations =
            new OracleCloudStorageReactiveOperations(configuration, asyncClient, regionProvider)
        OciListObjectsRequest firstRequest = null
        OciListObjectsRequest secondRequest = null
        int listInvocation = 0

        asyncClient.listObjects(_ as OciListObjectsRequest, _ as AsyncHandler<OciListObjectsRequest, OciListObjectsResponse>) >> {
                OciListObjectsRequest request,
                AsyncHandler<OciListObjectsRequest, OciListObjectsResponse> handler ->
            if (listInvocation++ == 0) {
                firstRequest = request
                handler.onSuccess(request, listObjectsResponse(["animals/cat.txt"], "animals/dog.txt"))
            } else {
                secondRequest = request
                handler.onSuccess(request, listObjectsResponse(["animals/dog.txt"], null))
            }
            Stub(Future)
        }

        when:
        Set<String> keys = Mono.from(operations.listObjects()).block()

        then:
        keys == ["animals/cat.txt", "animals/dog.txt"] as Set
        firstRequest.prefix == null
        firstRequest.startAfter == null
        firstRequest.limit == 1000
        secondRequest.startAfter == "animals/dog.txt"
    }

    void "listObjects page delegates the OCI continuation token"() {
        given:
        OracleCloudStorageConfiguration configuration = configuration()
        ObjectStorageAsyncClient asyncClient = Mock()
        RegionProvider regionProvider = Stub(RegionProvider)
        OracleCloudStorageReactiveOperations operations =
            new OracleCloudStorageReactiveOperations(configuration, asyncClient, regionProvider)
        OciListObjectsRequest capturedRequest = null

        asyncClient.listObjects(_ as OciListObjectsRequest, _ as AsyncHandler<OciListObjectsRequest, OciListObjectsResponse>) >> {
                OciListObjectsRequest request,
                AsyncHandler<OciListObjectsRequest, OciListObjectsResponse> handler ->
            capturedRequest = request
            handler.onSuccess(request, listObjectsResponse(["animals/cat.txt"], "animals/dog.txt"))
            Stub(Future)
        }

        when:
        def page = Mono.from(operations.listObjects(new ListObjectsRequest(5, "animals/", "token-1"))).block()

        then:
        page.keys == ["animals/cat.txt"]
        page.continuationToken.orElse(null) == "animals/dog.txt"
        capturedRequest.prefix == "animals/"
        capturedRequest.startAfter == "token-1"
        capturedRequest.limit == 5
    }

    void "copy and retrieval delegate to the native async client"() {
        given:
        OracleCloudStorageConfiguration configuration = configuration()
        ObjectStorageAsyncClient asyncClient = Mock()
        RegionProvider regionProvider = Stub(RegionProvider) {
            getRegion() >> Region.US_ASHBURN_1
        }
        OracleCloudStorageReactiveOperations operations =
            new OracleCloudStorageReactiveOperations(configuration, asyncClient, regionProvider)
        CopyObjectRequest copyRequest = null

        asyncClient.copyObject(_ as CopyObjectRequest, _ as AsyncHandler<CopyObjectRequest, CopyObjectResponse>) >> {
                CopyObjectRequest request,
                AsyncHandler<CopyObjectRequest, CopyObjectResponse> handler ->
            copyRequest = request
            handler.onSuccess(request, CopyObjectResponse.builder().build())
            Mock(Future)
        }
        asyncClient.getObject(_, _ as AsyncHandler) >> { request, AsyncHandler handler ->
            handler.onSuccess(request, GetObjectResponse.builder()
                .eTag("etag")
                .contentType("text/plain")
                .inputStream(new ByteArrayInputStream("hello".bytes))
                .build())
            Mock(Future)
        }
        asyncClient.headObject(_, _ as AsyncHandler) >> { request, AsyncHandler handler ->
            handler.onSuccess(request, HeadObjectResponse.builder().build())
            Mock(Future)
        }
        asyncClient.deleteObject(_, _ as AsyncHandler) >> { request, AsyncHandler handler ->
            handler.onSuccess(request, DeleteObjectResponse.builder().build())
            Mock(Future)
        }

        when:
        Mono.from(operations.copy("source.txt", "dest.txt")).block()
        def retrieved = Mono.from(operations.retrieve("dest.txt")).block()
        boolean exists = Mono.from(operations.exists("dest.txt")).block()
        Mono.from(operations.delete("dest.txt")).block()

        then:
        copyRequest.bucketName == "bucket"
        copyRequest.namespaceName == "namespace"
        copyRequest.copyObjectDetails.sourceObjectName == "source.txt"
        copyRequest.copyObjectDetails.destinationObjectName == "dest.txt"
        copyRequest.copyObjectDetails.destinationBucket == "bucket"
        copyRequest.copyObjectDetails.destinationNamespace == "namespace"
        copyRequest.copyObjectDetails.destinationRegion == "us-ashburn-1"
        retrieved.present
        retrieved.get().key == "dest.txt"
        retrieved.get().inputStream.text == "hello"
        exists
    }

    void "upload errors are wrapped and not found checks stay optional"() {
        given:
        OracleCloudStorageConfiguration configuration = configuration()
        ObjectStorageAsyncClient asyncClient = Mock()
        RegionProvider regionProvider = Stub(RegionProvider)
        OracleCloudStorageReactiveOperations operations =
            new OracleCloudStorageReactiveOperations(configuration, asyncClient, regionProvider)
        UploadRequest request = UploadRequest.fromBytes("hello".bytes, "dir/file.txt")
        BmcException failure = new BmcException(500, "InternalError", null, "boom")

        asyncClient.putObject(_ as PutObjectRequest, _ as AsyncHandler<PutObjectRequest, PutObjectResponse>) >> {
                PutObjectRequest putObjectRequest,
                AsyncHandler<PutObjectRequest, PutObjectResponse> handler ->
            handler.onError(putObjectRequest, failure)
            Mock(Future)
        }
        asyncClient.getObject(_, _ as AsyncHandler) >> { requestArg, AsyncHandler handler ->
            handler.onError(requestArg, new BmcException(404, "NotFound", null, "not found"))
            Stub(Future)
        }
        asyncClient.headObject(_, _ as AsyncHandler) >> { requestArg, AsyncHandler handler ->
            handler.onError(requestArg, new BmcException(404, "NotFound", null, "not found"))
            Stub(Future)
        }

        when:
        Mono.from(operations.upload(request)).block()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof ObjectStorageException
        e.cause.cause == failure

        when:
        def retrieved = Mono.from(operations.retrieve("missing.txt")).block()
        boolean exists = Mono.from(operations.exists("missing.txt")).block()

        then:
        !retrieved.present
        !exists
    }

    private static OracleCloudStorageConfiguration configuration() {
        OracleCloudStorageConfiguration configuration = new OracleCloudStorageConfiguration("default")
        configuration.bucket = "bucket"
        configuration.namespace = "namespace"
        configuration
    }

    private static OciListObjectsResponse listObjectsResponse(List<String> keys, String continuationToken) {
        OciListObjectsResponse.builder()
            .listObjects(ListObjects.builder()
                .objects(keys.collect { key -> ObjectSummary.builder().name(key).build() })
                .nextStartWith(continuationToken)
                .build())
            .build()
    }
}
