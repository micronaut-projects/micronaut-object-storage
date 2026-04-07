package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.GetBucketRequest
import com.oracle.bmc.objectstorage.responses.GetBucketResponse
import io.micronaut.objectstorage.ObjectStorageException
import spock.lang.Specification

class OracleCloudBucketOperationsSpec extends Specification {

    void 'it delegates bucket lifecycle operations to OCI'() {
        given:
        OracleCloudStorageConfiguration configuration = configuration('namespace', 'compartment')
        ObjectStorage client = Mock()
        OracleCloudBucketOperations operations = new OracleCloudBucketOperations(configuration, client)

        when:
        operations.create('created')
        def retrieved = operations.retrieve('retrieved')
        def missing = operations.retrieve('missing')
        operations.delete('deleted')

        then:
        1 * client.createBucket({
            CreateBucketRequest request ->
                request.namespaceName == 'namespace' &&
                    request.createBucketDetails.compartmentId == 'compartment' &&
                    request.createBucketDetails.name == 'created'
        })
        1 * client.getBucket({
            GetBucketRequest request ->
                request.namespaceName == 'namespace' &&
                    request.bucketName == 'retrieved'
        }) >> GetBucketResponse.builder().build()
        1 * client.getBucket({
            GetBucketRequest request ->
                request.namespaceName == 'namespace' &&
                    request.bucketName == 'missing'
        }) >> { throw notFound() }
        1 * client.deleteBucket({
            DeleteBucketRequest request ->
                request.namespaceName == 'namespace' &&
                    request.bucketName == 'deleted'
        })
        0 * _

        and:
        retrieved.present
        retrieved.get().name() == 'retrieved'
        retrieved.get().nativeEntry() instanceof GetBucketResponse
        !missing.present
    }

    void 'it wraps OCI errors for bucket operations'() {
        given:
        OracleCloudStorageConfiguration configuration = configuration('namespace', 'compartment')
        ObjectStorage client = Mock()
        OracleCloudBucketOperations operations = new OracleCloudBucketOperations(configuration, client)

        when:
        operations.create('created')

        then:
        1 * client.createBucket(_ as CreateBucketRequest) >> { throw bmcException('create failed') }
        ObjectStorageException e = thrown()
        e.message == 'Error creating bucket with name [created] in namespace [namespace] in compartment [compartment]'

        when:
        operations.retrieve('retrieved')

        then:
        1 * client.getBucket(_ as GetBucketRequest) >> { throw bmcException('retrieve failed') }
        e = thrown()
        e.message == 'Error retrieving bucket with name [retrieved] in namespace [namespace]'

        when:
        operations.delete('deleted')

        then:
        1 * client.deleteBucket(_ as DeleteBucketRequest) >> { throw bmcException('delete failed') }
        e = thrown()
        e.message == 'Error deleting bucket with name [deleted] in namespace [namespace]'
    }

    private static OracleCloudStorageConfiguration configuration(String namespace, String compartmentId) {
        def configuration = new OracleCloudStorageConfiguration('default')
        configuration.namespace = namespace
        configuration.compartmentId = compartmentId
        configuration.bucket = 'configured-default'
        configuration
    }

    private static BmcException bmcException(String message) {
        BmcException.createClientSide(message, null, null, null)
    }

    private static BmcException notFound() {
        new BmcException(404, 'NotAuthorizedOrNotFound', 'missing', null as String, null as Throwable)
    }
}
