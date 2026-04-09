package io.micronaut.objectstorage.tus

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class TusControllerSpec extends Specification implements TestPropertyProvider {

    @Inject
    TusController controller

    @Inject
    LocalStorageOperations operations

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
    }

    @Override
    Map<String, String> getProperties() {
        [
            (LocalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (TusModuleConfiguration.PREFIX + '.enabled')         : 'true'
        ]
    }

    void 'it can create resume and finalize a tus upload against local storage'() {
        given:
        def createRequest = HttpRequest.POST('/tus/default', '')
            .header(TusHeaders.RESUMABLE, TusHeaders.VERSION)
            .header(TusHeaders.LENGTH, '11')
            .header(TusHeaders.METADATA, TusMetadataCodec.encode([
                key        : 'photos/hello.txt',
                contentType: 'text/plain',
                owner      : 'micronaut'
            ]))

        when:
        def createResponse = controller.create('default', TusHeaders.VERSION, 11, createRequest.headers.get(TusHeaders.METADATA))
        String location = createResponse.header('Location')
        String uploadId = location.tokenize('/').last()

        then:
        createResponse.status() == HttpStatus.CREATED
        createResponse.header(TusHeaders.OFFSET) == '0'
        location == '/tus/default/' + uploadId

        when:
        def firstPatch = controller.patch('default', uploadId, TusHeaders.VERSION, 0L, 'hello'.bytes)
        def head = controller.head('default', uploadId, TusHeaders.VERSION)
        def secondPatch = controller.patch('default', uploadId, TusHeaders.VERSION, 5L, ' world'.bytes)

        then:
        firstPatch.status() == HttpStatus.NO_CONTENT
        firstPatch.header(TusHeaders.OFFSET) == '5'
        head.status() == HttpStatus.NO_CONTENT
        head.header(TusHeaders.OFFSET) == '5'
        secondPatch.status() == HttpStatus.NO_CONTENT
        secondPatch.header(TusHeaders.OFFSET) == '11'
        operations.retrieve('photos/hello.txt').present
        operations.retrieve('photos/hello.txt').get().inputStream.text == 'hello world'
        operations.retrieve('photos/hello.txt').get().metadata == [owner: 'micronaut']
    }

    void 'it rejects stale offsets without corrupting the upload'() {
        given:
        String location = createUpload('photos/stale.txt', 8)
        String uploadId = location.tokenize('/').last()
        controller.patch('default', uploadId, TusHeaders.VERSION, 0L, 'abcd'.bytes)

        when:
        controller.patch('default', uploadId, TusHeaders.VERSION, 0L, 'efgh'.bytes)

        then:
        def e = thrown(HttpStatusException)
        e.status == HttpStatus.CONFLICT
        controller.head('default', uploadId, TusHeaders.VERSION).header(TusHeaders.OFFSET) == '4'
        !operations.retrieve('photos/stale.txt').present
    }

    void 'it can abort an in-progress upload'() {
        given:
        String location = createUpload('photos/abort.txt', 4)
        String uploadId = location.tokenize('/').last()

        when:
        def deleteResponse = controller.delete('default', uploadId, TusHeaders.VERSION)
        def headResponse = controller.head('default', uploadId, TusHeaders.VERSION)

        then:
        deleteResponse.status() == HttpStatus.NO_CONTENT
        headResponse.status() == HttpStatus.GONE
        !operations.retrieve('photos/abort.txt').present
    }

    void 'it rejects missing or unsupported Tus-Resumable headers'() {
        given:
        String uploadId = createUpload('photos/versioned.txt', 4).tokenize('/').last()

        when:
        controller.create('default', null, 1, TusMetadataCodec.encode([key: 'photos/missing.txt']))

        then:
        def createException = thrown(HttpStatusException)
        createException.status == HttpStatus.PRECONDITION_FAILED

        when:
        controller.head('default', uploadId, '0.2.2')

        then:
        def headException = thrown(HttpStatusException)
        headException.status == HttpStatus.PRECONDITION_FAILED

        when:
        controller.patch('default', uploadId, null, 0L, 'ab'.bytes)

        then:
        def patchException = thrown(HttpStatusException)
        patchException.status == HttpStatus.PRECONDITION_FAILED

        when:
        controller.delete('default', uploadId, null)

        then:
        def deleteException = thrown(HttpStatusException)
        deleteException.status == HttpStatus.PRECONDITION_FAILED
    }

    void 'it decodes empty Upload-Metadata values'() {
        expect:
        TusMetadataCodec.decode('filename Zm9vLnR4dA==,flag,empty ') == [
            filename: 'foo.txt',
            flag    : '',
            empty   : ''
        ]
    }

    private String createUpload(String key, int length) {
        controller.create('default', TusHeaders.VERSION, length, TusMetadataCodec.encode([key: key]))
            .header('Location')
    }
}
