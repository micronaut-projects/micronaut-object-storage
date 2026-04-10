package io.micronaut.objectstorage.s3compat

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.StreamedFile
import spock.lang.Specification

class S3CompatibilityControllerSpec extends Specification {

    void 'it exposes path-style CRUD and list-objects-v2 for a configured bucket'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.object-storage.local.default.enabled'    : 'true',
            'micronaut.object-storage.s3-compat.assets.storage' : 'default',
            'micronaut.object-storage.s3-compat.assets.enabled' : 'true',
        ])
        S3CompatibilityController controller = context.getBean(S3CompatibilityController)
        byte[] body = 'hello world'.bytes

        when:
        def putResponse = controller.putObject(
            'assets',
            'docs/hello.txt',
            null,
            null,
            HttpRequest.PUT('/assets/docs/hello.txt', body).contentType(MediaType.TEXT_PLAIN_TYPE),
            new ByteArrayInputStream(body)
        )
        def headResponse = controller.headObject('assets', 'docs/hello.txt', HttpRequest.HEAD('/assets/docs/hello.txt'))
        def getResponse = controller.getObject('assets', 'docs/hello.txt', null, null, 1000, HttpRequest.GET('/assets/docs/hello.txt'))
        def listResponse = controller.listObjectsV2('assets', 2, 'docs/', null, 1000, HttpRequest.GET('/assets?list-type=2&prefix=docs/&max-keys=1000'))
        def deleteResponse = controller.deleteObject('assets', 'docs/hello.txt', null, HttpRequest.DELETE('/assets/docs/hello.txt'))
        def missingResponse = controller.getObject('assets', 'docs/hello.txt', null, null, 1000, HttpRequest.GET('/assets/docs/hello.txt'))

        then:
        putResponse.status() == HttpStatus.OK
        putResponse.header('ETag')
        headResponse.status() == HttpStatus.OK
        headResponse.header('Content-Length') == '11'

        and:
        getResponse.status() == HttpStatus.OK
        StreamedFile streamedFile = getResponse.body() as StreamedFile
        streamedFile.inputStream.text == 'hello world'
        getResponse.header('Content-Type') == 'text/plain'

        and:
        listResponse.status() == HttpStatus.OK
        listResponse.body().contains('<Name>assets</Name>')
        listResponse.body().contains('<Prefix>docs/</Prefix>')
        listResponse.body().contains('<Key>docs/hello.txt</Key>')

        and:
        deleteResponse.status() == HttpStatus.NO_CONTENT
        missingResponse.status() == HttpStatus.NOT_FOUND

        cleanup:
        context.close()
    }

    void 'it returns s3 xml errors for missing buckets and keys'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.object-storage.local.default.enabled'    : 'true',
            'micronaut.object-storage.s3-compat.assets.storage' : 'default',
            'micronaut.object-storage.s3-compat.assets.enabled' : 'true',
        ])
        S3CompatibilityController controller = context.getBean(S3CompatibilityController)

        when:
        def missingBucket = controller.getObject('missing', 'docs/hello.txt', null, null, 1000, HttpRequest.GET('/missing/docs/hello.txt'))
        def missingKey = controller.getObject('assets', 'docs/hello.txt', null, null, 1000, HttpRequest.GET('/assets/docs/hello.txt'))

        then:
        missingBucket.status() == HttpStatus.NOT_FOUND
        missingBucket.contentType.get() == MediaType.APPLICATION_XML_TYPE
        missingBucket.body().contains('<Code>NoSuchBucket</Code>')
        missingBucket.body().contains('<BucketName>missing</BucketName>')

        and:
        missingKey.status() == HttpStatus.NOT_FOUND
        missingKey.contentType.get() == MediaType.APPLICATION_XML_TYPE
        missingKey.body().contains('<Code>NoSuchKey</Code>')
        missingKey.body().contains('<Key>docs/hello.txt</Key>')

        cleanup:
        context.close()
    }

    void 'it returns an xml invalid request error for unsupported list variants'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.object-storage.local.default.enabled'    : 'true',
            'micronaut.object-storage.s3-compat.assets.storage' : 'default',
            'micronaut.object-storage.s3-compat.assets.enabled' : 'true',
        ])
        S3CompatibilityController controller = context.getBean(S3CompatibilityController)

        when:
        def response = controller.listObjectsV2('assets', 1, null, null, 1000, HttpRequest.GET('/assets?list-type=1&max-keys=1000'))

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.contentType.get() == MediaType.APPLICATION_XML_TYPE
        response.body().contains('<Code>InvalidRequest</Code>')
        response.body().contains('list-type=2')

        cleanup:
        context.close()
    }

    void 'it reports multipart as not implemented for local-backed buckets'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.object-storage.local.default.enabled'    : 'true',
            'micronaut.object-storage.s3-compat.assets.storage' : 'default',
            'micronaut.object-storage.s3-compat.assets.enabled' : 'true',
        ])
        S3CompatibilityController controller = context.getBean(S3CompatibilityController)

        when:
        def response = controller.postObject('assets', 'docs/big.bin', null, HttpRequest.POST('/assets/docs/big.bin?uploads', ''), '')

        then:
        response.status() == HttpStatus.NOT_IMPLEMENTED
        response.contentType.get() == MediaType.APPLICATION_XML_TYPE
        response.body().contains('<Code>NotImplemented</Code>')
        response.body().contains('Multipart uploads are only supported')

        cleanup:
        context.close()
    }

    void 'it rejects complete-multipart xml bodies that declare a doctype'() {
        given:
        def method = S3CompatibilityController.getDeclaredMethod('parseCompletedParts', String)
        method.accessible = true
        String body = '''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE CompleteMultipartUpload [
<!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<CompleteMultipartUpload>
  <Part>
    <PartNumber>1</PartNumber>
    <ETag>&xxe;</ETag>
  </Part>
</CompleteMultipartUpload>'''

        when:
        method.invoke(null, body)

        then:
        def e = thrown(java.lang.reflect.InvocationTargetException)
        e.cause instanceof IllegalArgumentException
        e.cause.message == 'Unable to parse the CompleteMultipartUpload request body'
    }
}
