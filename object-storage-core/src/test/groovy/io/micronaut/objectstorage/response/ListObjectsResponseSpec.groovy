package io.micronaut.objectstorage.response

import spock.lang.Specification
import spock.lang.Subject

@Subject(ListObjectsResponse)
class ListObjectsResponseSpec extends Specification {

    void "it exposes ordered immutable keys"() {
        given:
        def source = ['b', 'a']

        when:
        def response = new ListObjectsResponse(source)
        source << 'c'

        then:
        response.keys == ['b', 'a']

        when:
        response.keys << 'd'

        then:
        thrown(UnsupportedOperationException)
    }

    void "it exposes empty continuation token semantics"() {
        expect:
        new ListObjectsResponse(['a'], token).continuationToken == expected

        where:
        token      || expected
        null       || Optional.empty()
        ''         || Optional.empty()
        'token-1'  || Optional.of('token-1')
    }

    void "it preserves provided key order and token"() {
        when:
        def response = new ListObjectsResponse(['prefix/b', 'prefix/a'], 'token-2')

        then:
        response.keys == ['prefix/b', 'prefix/a']
        response.continuationToken.get() == 'token-2'
    }
}
