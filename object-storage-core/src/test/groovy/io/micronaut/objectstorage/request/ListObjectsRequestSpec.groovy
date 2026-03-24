package io.micronaut.objectstorage.request

import spock.lang.Specification
import spock.lang.Subject

@Subject(ListObjectsRequest)
class ListObjectsRequestSpec extends Specification {

    void "it creates request with page size only"() {
        when:
        def request = new ListObjectsRequest(25)

        then:
        request.pageSize == 25
        request.getPrefix().empty
        request.getContinuationToken().empty
    }

    void "it normalizes null and empty prefix to absent"() {
        expect:
        new ListObjectsRequest(10, prefix).getPrefix().empty

        where:
        prefix << [null, ""]
    }

    void "it preserves non empty prefix and continuation token"() {
        when:
        def request = new ListObjectsRequest(15, "folder/", "token-1")

        then:
        request.pageSize == 15
        request.getPrefix().get() == "folder/"
        request.getContinuationToken().get() == "token-1"
    }

    void "it allows empty continuation token semantics as absent"() {
        expect:
        new ListObjectsRequest(10, "prefix", token).getContinuationToken() == expected

        where:
        token      || expected
        null       || Optional.empty()
        ''         || Optional.empty()
        'token-1'  || Optional.of('token-1')
    }

    void "it rejects non positive page size"() {
        when:
        new ListObjectsRequest(pageSize)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'pageSize must be greater than 0'

        where:
        pageSize << [0, -1]
    }
}
