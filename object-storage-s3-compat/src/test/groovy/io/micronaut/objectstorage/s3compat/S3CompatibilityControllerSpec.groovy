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
            HttpRequest.PUT('/assets/docs/hello.txt', body).contentType(MediaType.TEXT_PLAIN_TYPE),
            body
        )
        def headResponse = controller.headObject('assets', 'docs/hello.txt')
        def getResponse = controller.getObject('assets', 'docs/hello.txt')
        def listResponse = controller.listObjectsV2('assets', 2, 'docs/', null, 1000)
        def deleteResponse = controller.deleteObject('assets', 'docs/hello.txt')
        def missingResponse = controller.getObject('assets', 'docs/hello.txt')

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
}
