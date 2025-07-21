package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.request.InputStreamUploadRequest
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification
class AwsS3OperationsSpec extends Specification {

    def 'should create RequestBody from input stream upload request with content size'() {
        given:
        def inputStream = new ByteArrayInputStream('Stream content'.bytes)
        def uploadRequest = new InputStreamUploadRequest(inputStream, 'key', 'application/json', 12L)
        def operations = new AwsS3Operations(Mock(AwsS3Configuration), Mock(S3Client), Mock(InputStreamMapper))

        when:
        def requestBody = operations.getRequestBody(uploadRequest)

        then:
        requestBody.optionalContentLength().get() == 12L
        requestBody.contentStreamProvider().name() == 'Stream'
    }

    def 'should create RequestBody from input stream upload request without content size'() {
        given:
        def inputStream = new ByteArrayInputStream('Stream content'.bytes)
        def uploadRequest = new InputStreamUploadRequest(inputStream, 'key', 'application/json', null)
        def inputStreamMapper = Mock(InputStreamMapper)
        inputStreamMapper.toByteArray(inputStream) >> 'Stream content'.bytes
        def operations = new AwsS3Operations(Mock(AwsS3Configuration), Mock(S3Client), inputStreamMapper)

        when:
        def requestBody = operations.getRequestBody(uploadRequest)

        then:
        requestBody.optionalContentLength().get() == 0
        requestBody.contentStreamProvider().name() == 'Stream'
    }

}
