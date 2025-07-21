package io.micronaut.objectstorage.request

import spock.lang.Specification
import spock.lang.Subject

@Subject(InputStreamUploadRequest)
class InputStreamUploadRequestSpec extends Specification {
    def 'should return content type when it is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def contentType = request.getContentType()

        then:
        contentType.isPresent()
        contentType.get() == 'application/json'
    }

    def 'should return empty optional when content type is not set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', null, 123L)

        when:
        def contentType = request.getContentType()

        then:
        !contentType.isPresent()
    }

    def 'should return key when it is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def key = request.getKey()

        then:
        key == 'key'
    }

    def 'should return content size when it is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def contentSize = request.getContentSize()

        then:
        contentSize.isPresent()
        contentSize.get() == 123L
    }

    def 'should return empty optional when content size is not set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', null)

        when:
        def contentSize = request.getContentSize()

        then:
        !contentSize.isPresent()
    }

    def 'should return input stream when it is set'() {
        given:
        def inputStream = Mock(InputStream)
        def request = new InputStreamUploadRequest(inputStream, 'key', 'application/json', 123L)

        when:
        def resultStream = request.getInputStream()

        then:
        resultStream == inputStream
    }

    def 'should return empty metadata when none is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def metadata = request.getMetadata()

        then:
        metadata.isEmpty()
    }
}
