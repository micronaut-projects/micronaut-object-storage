package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.Region
import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.BucketSummary
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest
import com.oracle.bmc.objectstorage.responses.ListBucketsResponse
import io.micronaut.objectstorage.ObjectStorageException
import spock.lang.Specification

class OracleCloudBucketOperationsSpec extends Specification {

    void 'it delegates CRUD bucket operations to OCI and creates scoped storage operations'() {
        given:
        OracleCloudNamespaceConfiguration configuration = configuration('namespace', 'compartment')
        ObjectStorage client = Mock()
        RegionProvider regionProvider = Stub() {
            getRegion() >> Region.EU_MARSEILLE_1
        }
        OracleCloudBucketOperations operations = new OracleCloudBucketOperations(configuration, client, regionProvider)

        when:
        operations.createBucket('created')
        operations.deleteBucket('deleted')
        def buckets = operations.listBuckets()
        def storage = operations.storageForBucket('scoped')
        Set<String> scopedListing = storage.listObjects()

        then:
        1 * client.createBucket({
            CreateBucketRequest request ->
                request.namespaceName == 'namespace' &&
                    request.createBucketDetails.compartmentId == 'compartment' &&
                    request.createBucketDetails.name == 'created'
        })
        1 * client.deleteBucket({
            DeleteBucketRequest request ->
                request.namespaceName == 'namespace' &&
                    request.bucketName == 'deleted'
        })
        1 * client.listBuckets({
            ListBucketsRequest request ->
                request.namespaceName == 'namespace' &&
                    request.compartmentId == 'compartment' &&
                    request.page == null
        }) >> listBucketsResponse(['first'], 'page-2')
        1 * client.listBuckets({
            ListBucketsRequest request ->
                request.namespaceName == 'namespace' &&
                    request.compartmentId == 'compartment' &&
                    request.page == 'page-2'
        }) >> listBucketsResponse(['second'], null)
        1 * client.listObjects({
            ListObjectsRequest request ->
                request.namespaceName == 'namespace' &&
                    request.bucketName == 'scoped' &&
                    request.limit == 1000 &&
                    request.startAfter == null &&
                    request.prefix == null
        }) >> OracleCloudStoragePaginationSpec.ociListResponse(['object-1'], null)
        0 * _

        and:
        buckets == ['first', 'second'] as Set
        storage instanceof OracleCloudStorageOperations
        scopedListing == ['object-1'] as Set
    }

    void 'it wraps OCI errors for bucket operations'() {
        given:
        OracleCloudNamespaceConfiguration configuration = configuration('namespace', 'compartment')
        ObjectStorage client = Mock()
        OracleCloudBucketOperations operations = new OracleCloudBucketOperations(configuration, client, Stub(RegionProvider))

        when:
        operations.createBucket('created')

        then:
        1 * client.createBucket(_ as CreateBucketRequest) >> { throw bmcException('create failed') }
        ObjectStorageException e = thrown()
        e.message == 'Error creating bucket with name [created] in namespace [namespace] in compartment [compartment]'

        when:
        operations.deleteBucket('deleted')

        then:
        1 * client.deleteBucket(_ as DeleteBucketRequest) >> { throw bmcException('delete failed') }
        e = thrown()
        e.message == 'Error deleting bucket with name [deleted] in namespace [namespace]'

        when:
        operations.listBuckets()

        then:
        1 * client.listBuckets(_ as ListBucketsRequest) >> { throw bmcException('list failed') }
        e = thrown()
        e.message == 'Error listing buckets in namespace [namespace] in compartment [compartment]'
    }

    void 'it rejects null bucket names for scoped storage operations'() {
        given:
        OracleCloudBucketOperations operations = new OracleCloudBucketOperations(configuration('namespace', 'compartment'), Mock(ObjectStorage), Stub(RegionProvider))

        when:
        operations.storageForBucket(null)

        then:
        def e = thrown(NullPointerException)
        e.message == 'bucket'
    }

    private static OracleCloudNamespaceConfiguration configuration(String namespace, String compartmentId) {
        def configuration = new OracleCloudNamespaceConfiguration()
        configuration.namespace = namespace
        configuration.compartmentId = compartmentId
        configuration
    }

    private static ListBucketsResponse listBucketsResponse(List<String> names, String nextPage) {
        List<BucketSummary> items = names.collect { BucketSummary.builder().name(it).build() }
        ListBucketsResponse.builder()
            .__httpStatusCode__(200)
            .items(items)
            .opcNextPage(nextPage)
            .build()
    }

    private static BmcException bmcException(String message) {
        BmcException.createClientSide(message, null, null, null)
    }
}
