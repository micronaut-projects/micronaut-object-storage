package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.auth.RegionProvider
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.ListObjects
import com.oracle.bmc.objectstorage.model.ObjectSummary
import io.micronaut.objectstorage.ObjectStorageException
import spock.lang.Specification

class OracleCloudStorageFailureSpec extends Specification {

    ObjectStorage client = Mock()
    OracleCloudStorageConfiguration configuration = Stub() {
        getBucket() >> 'bucket'
        getNamespace() >> 'namespace'
    }
    OracleCloudStorageOperations operations = new OracleCloudStorageOperations(configuration, client, Stub(RegionProvider))

    void 'propagates status #status from #operation without treating it as absence'() {
        given:
        def failure = new BmcException(status, 'ProviderFailure', 'unavailable', 'request-id')
        client.getObject(_) >> { throw failure }
        client.headObject(_) >> { throw failure }
        client.deleteObject(_) >> { throw failure }

        when:
        operations."$operation"('key')

        then:
        def exception = thrown(ObjectStorageException)
        exception.cause.is(failure)
        0 * client.listObjects(_)

        where:
        [operation, status] << [['retrieve', 'exists', 'delete'], [-1, 401, 403, 429, 500, 503]].combinations()
    }

    void 'confirms #serviceCode absence through an authorized exact-key lookup for #operation'() {
        given:
        def missing = new BmcException(404, serviceCode, 'not found', 'request-id')
        client.getObject(_) >> { throw missing }
        client.headObject(_) >> { throw missing }
        client.deleteObject(_) >> { throw missing }

        when:
        def result = operations."$operation"('key')

        then:
        1 * client.listObjects({ request ->
            request.bucketName == 'bucket' && request.namespaceName == 'namespace' &&
                request.prefix == 'key' && request.limit == 1
        }) >> listed('key-sibling')
        operation == 'retrieve' ? result == Optional.empty() :
            operation == 'exists' ? !result : result.__httpStatusCode__ == 204

        where:
        [operation, serviceCode] << [['retrieve', 'exists', 'delete'], ['NotAuthorizedOrNotFound', 'Unknown']].combinations()
    }

    void 'does not hide a failed #operation when the object is still listed'() {
        given:
        def failure = new BmcException(404, 'NotAuthorizedOrNotFound', 'not found', 'request-id')
        client.getObject(_) >> { throw failure }
        client.deleteObject(_) >> { throw failure }
        client.listObjects(_) >> listed('key')

        when:
        operations."$operation"('key')

        then:
        def exception = thrown(ObjectStorageException)
        exception.cause.is(failure)

        where:
        operation << ['retrieve', 'delete']
    }

    void 'reports an existing object when HEAD returns a bodyless 404 but listing finds it'() {
        given:
        client.headObject(_) >> { throw new BmcException(404, 'Unknown', 'not found', 'request-id') }
        client.listObjects(_) >> listed('key')

        expect:
        operations.exists('key')
    }

    void 'does not infer absence when the confirming listing fails for #operation'() {
        given:
        def missing = new BmcException(404, 'Unknown', 'not found', 'request-id')
        def denied = new BmcException(403, 'NotAuthorized', 'forbidden', 'request-id')
        client.getObject(_) >> { throw missing }
        client.headObject(_) >> { throw missing }
        client.deleteObject(_) >> { throw missing }
        client.listObjects(_) >> { throw denied }

        when:
        operations."$operation"('key')

        then:
        def exception = thrown(ObjectStorageException)
        exception.cause.is(denied)

        where:
        operation << ['retrieve', 'exists', 'delete']
    }

    void 'deletion can be retried after a lost response and when already absent'() {
        given:
        def lostResponse = BmcException.createClientSide('response lost', null, null, null)
        def missing = new BmcException(404, 'NotAuthorizedOrNotFound', 'not found', 'request-id')

        when:
        operations.delete('key')

        then:
        1 * client.deleteObject(_) >> { throw lostResponse }
        thrown(ObjectStorageException)

        when:
        def retry = operations.delete('key')
        def repeated = operations.delete('key')

        then:
        2 * client.deleteObject(_) >> { throw missing }
        2 * client.listObjects(_) >> listed()
        retry.__httpStatusCode__ == 204
        repeated.__httpStatusCode__ == 204
    }

    private static com.oracle.bmc.objectstorage.responses.ListObjectsResponse listed(String... keys) {
        return com.oracle.bmc.objectstorage.responses.ListObjectsResponse.builder()
            .listObjects(ListObjects.builder().objects(keys.collect { ObjectSummary.builder().name(it).build() }).build())
            .build()
    }
}
