package io.micronaut.objectstorage.oraclecloud


import io.micronaut.context.annotation.Property
import io.micronaut.objectstorage.request.PresignRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Requires

/**
 * Tests presign and invalidate for the Oracle backend using a mocked OCI SDK client.
 */
@Property(name = "spec.name", value = OracleCloudPresignSpec.SPEC_NAME)
@Requires({ env.ORACLE_CLOUD_TEST_NAMESPACE && env.ORACLE_CLOUD_TEST_COMPARTMENT_ID })
@MicronautTest
class OracleCloudPresignSpec extends OracleCloudStorageCloudSpec {
    static final String SPEC_NAME = 'OracleCloudPresignSpec'

    void "presigned URLs can be created and invalidated"() {
        when:
        def req = PresignRequest.builder("doc.pdf", operation).build()
        def resp = oracleCloudStorageOperations.presign(req)

        then:
        resp.url().toString() == 'https://objectstorage.example.com/p/parId'

        then:
        oracleCloudStorageOperations.invalidatePresignedRequest(resp.url())
        // We could also do an actual upload/download with the URL here, but we trust that object storage works as advertised.

        where:
        operation << [PresignRequest.Operation.DOWNLOAD, PresignRequest.Operation.UPLOAD]
    }
}
