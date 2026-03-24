package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.ListObjects
import com.oracle.bmc.objectstorage.model.ObjectSummary
import io.micronaut.objectstorage.request.ListObjectsRequest
import spock.lang.Specification

class OracleCloudStoragePaginationSpec extends Specification {

    void 'it replays OCI nextStartWith tokens and exhausts legacy listing'() {
        given:
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> 'bucket'
            getNamespace() >> 'namespace'
        }
        RegionProvider regionProvider = Stub()
        ObjectStorage client = Mock()
        OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, regionProvider)

        when:
        def firstPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', 'animals/cat.txt'))
        def replayedFirstPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', 'animals/cat.txt'))
        def secondPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', firstPage.continuationToken.orElse(null)))
        def replayedSecondPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', firstPage.continuationToken.orElse(null)))
        def terminalPage = operations.listObjects(new ListObjectsRequest(2, 'animals/', secondPage.continuationToken.orElse(null)))
        def allKeys = operations.listObjects()

        then:
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == 'animals/' &&
                request.limit == 2 &&
                request.startAfter == 'animals/cat.txt'
        }) >> ociListResponse(['animals/dog.txt', 'animals/mammals/fox.txt'], 'opaque-next-1')
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == 'animals/' &&
                request.limit == 2 &&
                request.startAfter == 'animals/cat.txt'
        }) >> ociListResponse(['animals/dog.txt', 'animals/mammals/fox.txt'], 'opaque-next-1')
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == 'animals/' &&
                request.limit == 2 &&
                request.startAfter == 'opaque-next-1'
        }) >> ociListResponse(['animals/zebra.txt'], 'opaque-next-2')
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == 'animals/' &&
                request.limit == 2 &&
                request.startAfter == 'opaque-next-1'
        }) >> ociListResponse(['animals/zebra.txt'], 'opaque-next-2')
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == 'animals/' &&
                request.limit == 2 &&
                request.startAfter == 'opaque-next-2'
        }) >> ociListResponse([], null)
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == null &&
                request.limit == 1000 &&
                request.startAfter == null
        }) >> ociListResponse(['animals/cat.txt', 'animals/dog.txt'], 'opaque-all-1')
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' &&
                request.namespaceName == 'namespace' &&
                request.prefix == null &&
                request.limit == 1000 &&
                request.startAfter == 'opaque-all-1'
        }) >> ociListResponse(['animals/mammals/fox.txt', 'plants/oak.txt'], null)
        0 * _

        and:
        firstPage.keys == ['animals/dog.txt', 'animals/mammals/fox.txt']
        firstPage.continuationToken == Optional.of('opaque-next-1')
        replayedFirstPage.keys == firstPage.keys
        replayedFirstPage.continuationToken == firstPage.continuationToken
        secondPage.keys == ['animals/zebra.txt']
        secondPage.continuationToken == Optional.of('opaque-next-2')
        replayedSecondPage.keys == secondPage.keys
        replayedSecondPage.continuationToken == secondPage.continuationToken
        terminalPage.keys == []
        !terminalPage.continuationToken.present
        allKeys == ['animals/cat.txt', 'animals/dog.txt', 'animals/mammals/fox.txt', 'plants/oak.txt'] as LinkedHashSet
    }

    void 'it returns empty portable pages when OCI listing fails'() {
        given:
        OracleCloudStorageConfiguration configuration = Stub() {
            getBucket() >> 'bucket'
            getNamespace() >> 'namespace'
        }
        RegionProvider regionProvider = Stub()
        ObjectStorage client = Mock()
        OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, regionProvider)

        when:
        def response = operations.listObjects(new ListObjectsRequest(3, 'animals/', 'animals/cat.txt'))
        def allKeys = operations.listObjects()

        then:
        1 * client.listObjects(_ as com.oracle.bmc.objectstorage.requests.ListObjectsRequest) >> { throw bmcException('boom') }
        1 * client.listObjects(_ as com.oracle.bmc.objectstorage.requests.ListObjectsRequest) >> { throw bmcException('boom') }
        0 * _

        and:
        response.keys == []
        !response.continuationToken.present
        allKeys.isEmpty()
    }

    private static com.oracle.bmc.objectstorage.responses.ListObjectsResponse ociListResponse(List<String> keys, String nextStartWith) {
        def summaries = keys.collect { key -> ObjectSummary.builder().name(key).build() }
        def listObjects = ListObjects.builder()
            .objects(summaries)
            .nextStartWith(nextStartWith)
            .build()
        return com.oracle.bmc.objectstorage.responses.ListObjectsResponse.builder()
            .__httpStatusCode__(200)
            .listObjects(listObjects)
            .build()
    }

    private static BmcException bmcException(String message) {
        return BmcException.createClientSide(message, null, null, null)
    }
}
