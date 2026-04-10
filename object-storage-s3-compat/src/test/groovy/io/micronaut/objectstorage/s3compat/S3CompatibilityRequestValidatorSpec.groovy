package io.micronaut.objectstorage.s3compat

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import spock.lang.Specification

class S3CompatibilityRequestValidatorSpec extends Specification {

    void 'it rejects malformed x-amz-date values with an s3 auth error'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.object-storage.s3-compat.enabled'           : 'true',
            'micronaut.object-storage.s3-compat.auth-mode'         : 'sigv4',
            'micronaut.object-storage.s3-compat.access-key-id'     : 'test-access-key',
            'micronaut.object-storage.s3-compat.secret-access-key' : 'test-secret-key',
            'micronaut.object-storage.s3-compat.region'            : 'us-east-1',
        ])
        S3CompatibilityRequestValidator validator = context.getBean(S3CompatibilityRequestValidator)
        HttpRequest<?> request = HttpRequest.GET('/assets')
            .header(HttpHeaders.AUTHORIZATION, 'AWS4-HMAC-SHA256 Credential=test-access-key/20260410/us-east-1/s3/aws4_request,SignedHeaders=host;x-amz-content-sha256;x-amz-date,Signature=deadbeef')
            .header('x-amz-date', '2026041')
            .header('x-amz-content-sha256', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
            .header(HttpHeaders.HOST, 'localhost')

        when:
        def response = validator.validate(request).orElse(null)

        then:
        response.status() == HttpStatus.FORBIDDEN
        response.body().contains('<Code>AccessDenied</Code>')
        response.body().contains('Malformed x-amz-date header')

        cleanup:
        context.close()
    }

    void 'it rejects malformed percent-encoding in canonical query parsing'() {
        given:
        def method = S3CompatibilityRequestValidator.getDeclaredMethod('percentDecode', String)
        method.accessible = true

        when:
        method.invoke(null, '%ZZ')

        then:
        def e = thrown(java.lang.reflect.InvocationTargetException)
        e.cause instanceof IllegalArgumentException
        e.cause.message == 'Malformed query parameter encoding'
    }

    void 'it verifies the received payload hash when sigv4 supplies one'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.object-storage.s3-compat.enabled'           : 'true',
            'micronaut.object-storage.s3-compat.auth-mode'         : 'sigv4',
            'micronaut.object-storage.s3-compat.access-key-id'     : 'test-access-key',
            'micronaut.object-storage.s3-compat.secret-access-key' : 'test-secret-key',
            'micronaut.object-storage.s3-compat.region'            : 'us-east-1',
        ])
        S3CompatibilityRequestValidator validator = context.getBean(S3CompatibilityRequestValidator)
        HttpRequest<?> request = HttpRequest.PUT('/assets/docs/hello.txt', '')
            .header(HttpHeaders.AUTHORIZATION, 'AWS4-HMAC-SHA256 Credential=test-access-key/20260410/us-east-1/s3/aws4_request,SignedHeaders=host;x-amz-content-sha256;x-amz-date,Signature=deadbeef')
            .header('x-amz-date', '20260410T010203Z')
            .header('x-amz-content-sha256', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
            .header(HttpHeaders.HOST, 'localhost')

        when:
        def response = validator.validate(request, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb').orElse(null)

        then:
        response.status() == HttpStatus.FORBIDDEN
        response.body().contains('<Code>SignatureDoesNotMatch</Code>')
        response.body().contains('x-amz-content-sha256')

        cleanup:
        context.close()
    }
}
